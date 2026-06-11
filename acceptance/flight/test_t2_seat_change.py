"""T2 evolution suite: seat change (see T2_FEATURE.md).

Only collected when TASK=t2 — T1 runs are not graded against this file.
"""

import os

import pytest

from conftest import (
    book_seat,
    create_customer,
    create_flight,
    get_json,
    new_booking_id,
    parallel,
    seat_ids,
    seat_state,
    seats_taken,
    stats,
    wait_all_decided,
    wait_decided,
    wait_until,
)

pytestmark = pytest.mark.skipif(
    os.environ.get("TASK", "t1") != "t2",
    reason="T2 evolution suite (set TASK=t2)",
)


def _confirmed_booking(client, *, seat_count=10, price=500, balance=5_000):
    f = create_flight(client, seat_count=seat_count, seat_price=price)
    fid = f["id"]
    seats = seat_ids(f)
    cid = create_customer(client, balance=balance)
    bid = new_booking_id()
    book_seat(client, bid, cid, fid, seats[0])
    b = wait_decided(client, bid)
    assert b["status"] == "CONFIRMED", b
    return fid, cid, bid, seats, price


def test_change_seat_releases_old_acquires_new(client):
    fid, cid, bid, seats, price = _confirmed_booking(client)
    old, new = seats[0], seats[1]

    r = client.post(f"/bookings/{bid}/change-seat", json={"newSeat": new})
    assert r.status_code in (200, 202), r.text[:200]

    wait_until(lambda: get_json(client, f"/bookings/{bid}")["seat"] == new,
               what="booking moved to the new seat")
    wait_until(lambda: seat_state(client, fid, new).get("bookingId") == bid,
               what="new seat owned by the booking")
    wait_until(lambda: seat_state(client, fid, old)["available"],
               what="old seat released")
    # exactly one seat held by this booking; no charge/refund
    wait_until(lambda: len(seats_taken(client, fid)) == 1,
               what="exactly one seat held after change")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 5_000 - price,
               what="balance unaffected by seat change")


def test_change_into_taken_seat_retains_old(client):
    """If newSeat is already taken, the change fails and the OLD seat is kept."""
    fid, cid, bid, seats, price = _confirmed_booking(client)
    old = seats[0]
    # occupy the target seat with another confirmed booking
    other = create_customer(client, balance=price * 2)
    occupy = new_booking_id()
    book_seat(client, occupy, other, fid, seats[1])
    assert wait_decided(client, occupy)["status"] == "CONFIRMED"

    r = client.post(f"/bookings/{bid}/change-seat", json={"newSeat": seats[1]})
    assert r.status_code in (200, 202, 409), r.text[:200]

    # the booking retains its old seat; the target stays owned by the occupier
    wait_until(lambda: get_json(client, f"/bookings/{bid}")["seat"] == old,
               what="booking keeps its old seat when target is taken")
    wait_until(lambda: seat_state(client, fid, old).get("bookingId") == bid,
               what="old seat still owned by the booking")
    wait_until(lambda: seat_state(client, fid, seats[1]).get("bookingId") == occupy,
               what="target seat still owned by the occupier")
    wait_until(lambda: len(seats_taken(client, fid)) == 2,
               what="no seat lost, no double-hold")


def test_change_seat_idempotent(client):
    """N concurrent change requests to the same free seat -> one effect."""
    n = 8
    fid, cid, bid, seats, price = _confirmed_booking(client)
    new = seats[1]

    responses = parallel([
        (lambda: client.post(f"/bookings/{bid}/change-seat", json={"newSeat": new}))
        for _ in range(n)
    ])
    assert all(r.status_code in (200, 202, 409) for r in responses)

    wait_until(lambda: get_json(client, f"/bookings/{bid}")["seat"] == new,
               what="booking on the new seat")
    wait_until(lambda: len(seats_taken(client, fid)) == 1,
               what="exactly one seat held despite concurrent changes")
    wait_until(lambda: seat_state(client, fid, seats[0])["available"],
               what="old seat freed exactly once")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 5_000 - price,
               what="balance unaffected")


def test_concurrent_changes_into_same_target_at_most_one_wins(client):
    """Two bookings concurrently try to change INTO the same target seat:
    at most one succeeds; the loser retains its old seat; target owned once."""
    f = create_flight(client, seat_count=10, seat_price=300)
    fid = f["id"]
    seats = seat_ids(f)
    price = 300

    # two confirmed bookings on seats[0] and seats[1]; target is seats[2]
    setups = []
    for i in (0, 1):
        cid = create_customer(client, balance=price * 2)
        bid = new_booking_id()
        book_seat(client, bid, cid, fid, seats[i])
        assert wait_decided(client, bid)["status"] == "CONFIRMED"
        setups.append((cid, bid, seats[i]))

    target = seats[2]
    parallel([
        (lambda b=bid: client.post(f"/bookings/{b}/change-seat", json={"newSeat": target}))
        for (_, bid, _) in setups
    ])

    def settled() -> bool:
        on_target = [bid for (_, bid, _) in setups
                     if get_json(client, f"/bookings/{bid}")["seat"] == target]
        # at most one booking sits on the target seat
        assert len(on_target) <= 1, f"target double-booked: {on_target}"
        # the loser must still own its original seat
        for cid, bid, original in setups:
            cur = get_json(client, f"/bookings/{bid}")["seat"]
            if cur != target:
                assert cur == original, f"loser lost its seat: {bid} on {cur}"
        # exactly two seats are held overall (no seat lost, no extra hold)
        return len(seats_taken(client, fid)) == 2

    wait_until(settled, what="at most one wins the target; no seat lost")


def test_stats_unaffected_by_seat_change(client):
    before = stats(client)
    fid, cid, bid, seats, price = _confirmed_booking(client)

    def confirmed_counted() -> bool:
        return stats(client)["confirmed"] - before["confirmed"] == 1
    wait_until(confirmed_counted, what="confirmation counted")

    client.post(f"/bookings/{bid}/change-seat", json={"newSeat": seats[1]})
    wait_until(lambda: get_json(client, f"/bookings/{bid}")["seat"] == seats[1],
               what="seat changed")

    def unchanged() -> bool:
        s = stats(client)
        return (s["confirmed"] - before["confirmed"] == 1
                and s["rejected"] - before["rejected"] == 0
                and s["revenue"] - before["revenue"] == price)
    wait_until(unchanged, what="stats unaffected by seat change")
