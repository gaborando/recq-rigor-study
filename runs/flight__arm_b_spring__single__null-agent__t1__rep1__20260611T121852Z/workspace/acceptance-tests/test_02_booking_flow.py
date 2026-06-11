"""Booking lifecycle: confirmation, rejection paths, compensation, notifications, stats."""

from conftest import (
    book_seat,
    create_customer,
    create_flight,
    get_json,
    new_booking_id,
    notifications_for,
    seat_ids,
    seat_state,
    stats,
    wait_decided,
    wait_until,
)


def test_happy_path_confirmed(client):
    f = create_flight(client, seat_count=10, seat_price=300)
    fid = f["id"]
    seat = seat_ids(f)[0]
    cid = create_customer(client, balance=5_000)
    bid = new_booking_id()

    r = book_seat(client, bid, cid, fid, seat)
    assert r.status_code in (200, 202), r.text[:200]

    b = wait_decided(client, bid)
    assert b["status"] == "CONFIRMED", b
    assert b.get("total") == 300
    assert b["seat"] == seat

    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 4_700,
               what="balance charged exactly once")
    wait_until(lambda: not seat_state(client, fid, seat)["available"],
               what="seat shown taken")
    wait_until(lambda: seat_state(client, fid, seat).get("bookingId") == bid,
               what="seat owned by this booking")
    wait_until(lambda: any(n["bookingId"] == bid and n["status"] == "CONFIRMED"
                           for n in notifications_for(client, cid)),
               what="confirmation notification")


def test_seat_taken_rejected_nothing_charged(client):
    """A second booking for a seat already booked is REJECTED(SEAT_TAKEN)."""
    f = create_flight(client, seat_count=10, seat_price=300)
    fid = f["id"]
    seat = seat_ids(f)[0]
    cid = create_customer(client, balance=5_000)

    first = new_booking_id()
    book_seat(client, first, cid, fid, seat)
    assert wait_decided(client, first)["status"] == "CONFIRMED"

    second = new_booking_id()
    other = create_customer(client, balance=5_000)
    book_seat(client, second, other, fid, seat)
    b = wait_decided(client, second)
    assert b["status"] == "REJECTED", b
    assert b.get("reason") == "SEAT_TAKEN", b

    wait_until(lambda: get_json(client, f"/customers/{other}")["balance"] == 5_000,
               what="loser not charged")
    # seat still owned by the first booking only
    wait_until(lambda: seat_state(client, fid, seat).get("bookingId") == first,
               what="seat still owned by the winner")


def test_insufficient_funds_rejected_with_compensation(client):
    """The saga path: seat is HELD first, then must be RELEASED on charge failure."""
    f = create_flight(client, seat_count=10, seat_price=1_000)
    fid = f["id"]
    seat = seat_ids(f)[0]
    cid = create_customer(client, balance=500)   # cannot afford the seat
    bid = new_booking_id()

    book_seat(client, bid, cid, fid, seat)
    b = wait_decided(client, bid)
    assert b["status"] == "REJECTED", b
    assert b.get("reason") == "INSUFFICIENT_FUNDS", b

    # compensation: the transient hold is released; no residual effect
    wait_until(lambda: seat_state(client, fid, seat)["available"],
               what="held seat released (compensation)")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 500,
               what="no money taken")
    wait_until(lambda: any(n["bookingId"] == bid and n["status"] == "REJECTED"
                           for n in notifications_for(client, cid)),
               what="rejection notification")


def test_stats_reflect_decisions(client):
    before = stats(client)
    f = create_flight(client, seat_count=10, seat_price=250)
    fid = f["id"]
    seats = seat_ids(f)
    cid = create_customer(client, balance=250)   # affords exactly one seat

    ok, ko = new_booking_id(), new_booking_id()
    book_seat(client, ok, cid, fid, seats[0])     # -> CONFIRMED
    assert wait_decided(client, ok)["status"] == "CONFIRMED"
    book_seat(client, ko, cid, fid, seats[1])     # -> REJECTED (funds gone)
    assert wait_decided(client, ko)["status"] == "REJECTED"

    def converged() -> bool:
        s = stats(client)
        return (s["confirmed"] - before["confirmed"] == 1
                and s["rejected"] - before["rejected"] == 1
                and s["revenue"] - before["revenue"] == 250)

    wait_until(converged, what="stats convergence")
