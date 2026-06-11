"""Anti-gaming variant suite — NEVER enters an agent workspace.

Re-grades the final artifact on the same invariants with different shapes:
different cardinalities, more seats, interleaved mixed traffic. An
implementation that hard-coded the acceptance suite's literal patterns fails here.

Run from the repo root:  pytest variant/flight
(the local conftest re-exports the acceptance suite's fixtures and helpers)
"""

from conftest import (
    LONG_DEADLINE,
    book_seat,
    create_customer,
    create_flight,
    get_json,
    new_booking_id,
    notifications_for,
    parallel,
    rng,
    seat_ids,
    seats_taken,
    stats,
    wait_all_decided,
    wait_until,
)


def test_no_double_booking_heavier_contention(client):
    """Heavier contention than the acceptance suite: many racers, same seat,
    distinct customers — still exactly one winner, never double-booked."""
    n = 16
    price = 250
    f = create_flight(client, seat_count=30, seat_price=price)
    fid = f["id"]
    seat = rng.choice(seat_ids(f))
    customers = [create_customer(client, balance=price * 2) for _ in range(n)]

    bids = [new_booking_id() for _ in range(n)]
    parallel([
        (lambda b=b, c=c: book_seat(client, b, c, fid, seat))
        for b, c in zip(bids, customers)
    ])
    decided = wait_all_decided(client, bids)
    confirmed = [b for b in decided.values() if b["status"] == "CONFIRMED"]
    assert len(confirmed) == 1, f"double-booked: {len(confirmed)} winners"
    assert all(b.get("reason") == "SEAT_TAKEN"
               for b in decided.values() if b["status"] == "REJECTED")

    winner = confirmed[0]["bookingId"]
    wait_until(lambda: [s for s in seats_taken(client, fid)
                        if s["seat"] == seat][0].get("bookingId") == winner,
               what="seat owned by exactly one winner under heavy contention")


def test_duplicate_storm_with_more_replicas(client):
    m = 16  # heavier retry storm than the acceptance suite
    price = 333
    f = create_flight(client, seat_count=5, seat_price=price)
    fid = f["id"]
    seat = seat_ids(f)[0]
    cid = create_customer(client, balance=999)
    bid = new_booking_id()
    parallel([(lambda: book_seat(client, bid, cid, fid, seat)) for _ in range(m)])

    wait_until(lambda: get_json(client, f"/bookings/{bid}")["status"] == "CONFIRMED",
               what="booking decided")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 999 - price,
               what="single charge under heavy retry storm")
    wait_until(lambda: len(seats_taken(client, fid)) == 1,
               what="single seat held under heavy retry storm")
    wait_until(lambda: sum(1 for n in notifications_for(client, cid)
                           if n["bookingId"] == bid) == 1,
               what="single notification under heavy retry storm")


def test_mixed_interleaved_traffic_conservation(client):
    """Bookings (over a constrained seat set) and deposits interleaved
    concurrently; audit conservation on seats and money."""
    before = stats(client)
    price = 110
    seat_count = 6
    f = create_flight(client, seat_count=seat_count, seat_price=price)
    fid = f["id"]
    seats = seat_ids(f)
    start_balance = 8 * price
    cid = create_customer(client, balance=start_balance)

    bids = [new_booking_id() for _ in range(10)]
    deposit_amount = price
    n_deposits = 2
    ops = (
        [(lambda b=b, i=i: book_seat(client, b, cid, fid, seats[i % seat_count]))
         for i, b in enumerate(bids)]
        + [(lambda: client.post(f"/customers/{cid}/deposit", json={"amount": deposit_amount}))
           for _ in range(n_deposits)]
    )
    rng.shuffle(ops)
    parallel(ops, max_workers=8)

    decided = wait_all_decided(client, bids)
    n_confirmed = sum(1 for b in decided.values() if b["status"] == "CONFIRMED")
    # seats bound confirmations
    assert n_confirmed <= seat_count, f"oversold seats: {n_confirmed} > {seat_count}"

    def conserved() -> bool:
        s = stats(client)
        bal = get_json(client, f"/customers/{cid}")["balance"]
        taken = len(seats_taken(client, fid))
        money_in = start_balance + n_deposits * deposit_amount
        return (
            s["confirmed"] - before["confirmed"] == n_confirmed
            and s["revenue"] - before["revenue"] == n_confirmed * price
            and taken == n_confirmed                              # seats conserved
            and bal == money_in - n_confirmed * price             # money conserved
        )

    wait_until(conserved, deadline=LONG_DEADLINE,
               what="conservation under interleaved mixed traffic")
