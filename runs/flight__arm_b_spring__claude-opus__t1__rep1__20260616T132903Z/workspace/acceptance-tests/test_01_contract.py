"""Contract basics: resource creation, views, validation, unknown ids."""

from conftest import (
    create_customer,
    create_flight,
    get_json,
    seat_ids,
    wait_until,
)


def test_create_and_read_flight(client):
    f = create_flight(client, seat_count=12, seat_price=5_000)
    fid = f["id"]
    wait_until(lambda: len(get_json(client, f"/flights/{fid}")["seats"]) == 12,
               what="flight view with seats")
    view = get_json(client, f"/flights/{fid}")
    assert view["id"] == fid
    assert view["seatPrice"] == 5_000
    assert view["seatCount"] == 12
    # all seats start available, ids unique
    seats = view["seats"]
    assert all(s["available"] for s in seats)
    assert len(set(s["seat"] for s in seats)) == 12


def test_create_and_read_customer(client):
    cid = create_customer(client, balance=10_000)
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 10_000,
               what="customer view")


def test_unknown_ids_are_404(client):
    assert client.get("/flights/does-not-exist-xyz").status_code == 404
    assert client.get("/customers/does-not-exist-xyz").status_code == 404


def test_validation_rejects_malformed(client):
    # missing fields
    assert client.post("/flights", json={"seatCount": 5}).status_code == 400
    # invalid seat count / price
    assert client.post("/flights", json={"seatCount": 0, "seatPrice": 100}).status_code == 400
    assert client.post("/flights", json={"seatCount": 5, "seatPrice": 0}).status_code == 400
    cid = create_customer(client, balance=1_000)
    assert client.post(f"/customers/{cid}/deposit", json={"amount": -5}).status_code == 400
    assert client.post(f"/customers/{cid}/deposit", json={"amount": 0}).status_code == 400


def test_deposit_applies(client):
    cid = create_customer(client, balance=100)
    assert client.post(f"/customers/{cid}/deposit", json={"amount": 50}).status_code == 202
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 150,
               what="deposit applied")


def test_seat_ids_are_stable(client):
    """Seat ids reported at creation match those in the view (clients book by id)."""
    f = create_flight(client, seat_count=6, seat_price=100)
    fid = f["id"]
    wait_until(lambda: len(get_json(client, f"/flights/{fid}")["seats"]) == 6,
               what="flight seats materialized")
    created = set(seat_ids(f))
    viewed = set(s["seat"] for s in get_json(client, f"/flights/{fid}")["seats"])
    assert created == viewed, f"seat ids drifted: {created} vs {viewed}"
