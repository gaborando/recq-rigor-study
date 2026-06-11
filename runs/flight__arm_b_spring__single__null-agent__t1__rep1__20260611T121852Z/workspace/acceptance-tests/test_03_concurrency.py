"""Distributed-systems concurrency scenarios. These are part of the contract.

Each test races real HTTP requests and asserts system INVARIANTS (no
double-booking, conservation, exactly-once, no double-spend, no leaked seat)
rather than any particular interleaving.

The per-seat HOLD is the distributed lock: exactly one concurrent requester may
win a given seat — a seat is NEVER double-booked.
"""

from conftest import (
    DEADLINE,
    LONG_DEADLINE,
    book_seat,
    create_customer,
    create_flight,
    flight_view,
    get_json,
    new_booking_id,
    notifications_for,
    parallel,
    rng,
    seat_ids,
    seat_state,
    seats_taken,
    stats,
    wait_all_decided,
    wait_decided,
    wait_until,
)


def test_no_double_booking_same_seat(client):
    """N concurrent bookings for the SAME seat (the distributed lock): exactly
    one CONFIRMED, the rest REJECTED(SEAT_TAKEN); seat owned by exactly one
    booking; charged exactly once."""
    n = rng.randint(6, 10)
    price = 400
    f = create_flight(client, seat_count=30, seat_price=price)
    fid = f["id"]
    seat = seat_ids(f)[0]
    cid = create_customer(client, balance=n * price * 2)  # funds NOT the constraint

    bids = [new_booking_id() for _ in range(n)]
    responses = parallel([
        (lambda b=b: book_seat(client, b, cid, fid, seat)) for b in bids
    ])
    assert all(r.status_code in (200, 202) for r in responses)

    decided = wait_all_decided(client, bids)
    confirmed = [b for b in decided.values() if b["status"] == "CONFIRMED"]
    rejected = [b for b in decided.values() if b["status"] == "REJECTED"]

    assert len(confirmed) == 1, f"double-booked: {len(confirmed)} bookings won the seat"
    assert len(rejected) == n - 1
    assert all(b.get("reason") == "SEAT_TAKEN" for b in rejected)

    winner = confirmed[0]["bookingId"]
    wait_until(lambda: seat_state(client, fid, seat).get("bookingId") == winner,
               what="seat owned by exactly the one winner")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"]
               == n * price * 2 - price,
               what="charged exactly once for the single confirmed seat")


def test_seat_contention_across_many_seats(client):
    """N racers spread over K < N seats: exactly K confirmed, conservation on
    seats and money; no seat double-booked."""
    k = rng.randint(3, 6)
    n = k + 6
    price = 150
    f = create_flight(client, seat_count=k, seat_price=price)
    fid = f["id"]
    seats = seat_ids(f)
    cid = create_customer(client, balance=n * price * 2)  # funds NOT the constraint

    # spread n bookings over the k seats (round-robin so each seat is contended)
    bids = [new_booking_id() for _ in range(n)]
    parallel([
        (lambda b=b, i=i: book_seat(client, b, cid, fid, seats[i % k]))
        for i, b in enumerate(bids)
    ])

    decided = wait_all_decided(client, bids)
    confirmed = [b for b in decided.values() if b["status"] == "CONFIRMED"]

    assert len(confirmed) == k, f"expected exactly {k} confirmed, got {len(confirmed)}"
    # each confirmed booking owns a distinct seat
    owned = [b["seat"] for b in confirmed]
    assert len(set(owned)) == k, f"a seat was double-booked: {owned}"

    def conserved() -> bool:
        taken = seats_taken(client, fid)
        bal = get_json(client, f"/customers/{cid}")["balance"]
        return (len(taken) == k                                  # seats sold == seats unavailable
                and bal == n * price * 2 - k * price)            # charged exactly once per confirm
    wait_until(conserved, what="conservation: K seats sold == K taken == K charges")


def test_duplicate_booking_idempotency(client):
    """Same bookingId submitted M times concurrently (client retry storm):
    one seat, one charge, one notification — exactly one effect."""
    m = 8
    price = 400
    f = create_flight(client, seat_count=10, seat_price=price)
    fid = f["id"]
    seat = seat_ids(f)[0]
    cid = create_customer(client, balance=4_000)
    bid = new_booking_id()

    responses = parallel([
        (lambda: book_seat(client, bid, cid, fid, seat)) for _ in range(m)
    ])
    assert all(r.status_code in (200, 202) for r in responses)

    b = wait_decided(client, bid)
    assert b["status"] == "CONFIRMED", b

    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 4_000 - price,
               what="charged exactly once despite duplicates")
    wait_until(lambda: len(seats_taken(client, fid)) == 1,
               what="exactly one seat held despite duplicates")

    def exactly_one_notification() -> bool:
        ns = [n for n in notifications_for(client, cid) if n["bookingId"] == bid]
        assert len(ns) <= 1, f"duplicate notifications: {ns}"
        return len(ns) == 1

    wait_until(exactly_one_notification, what="exactly one notification")


def test_concurrent_deposit_no_lost_update(client):
    n = 20
    cid = create_customer(client, balance=0)
    responses = parallel([
        (lambda: client.post(f"/customers/{cid}/deposit", json={"amount": 5}))
        for _ in range(n)
    ])
    assert all(r.status_code == 202 for r in responses)
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 5 * n,
               deadline=LONG_DEADLINE, what=f"all {n} deposits applied")


def test_double_spend_race(client):
    """Customer affords exactly ONE seat; concurrent bookings of DIFFERENT seats
    race for the funds: exactly one CONFIRMED, balance ends 0, losers
    REJECTED(INSUFFICIENT_FUNDS), and their held seats are released."""
    n = 6
    price = 900
    f = create_flight(client, seat_count=n, seat_price=price)  # seats NOT the constraint
    fid = f["id"]
    seats = seat_ids(f)
    cid = create_customer(client, balance=price)               # affords exactly one

    bids = [new_booking_id() for _ in range(n)]
    parallel([
        (lambda b=b, s=s: book_seat(client, b, cid, fid, s))
        for b, s in zip(bids, seats)
    ])

    decided = wait_all_decided(client, bids)
    confirmed = [b for b in decided.values() if b["status"] == "CONFIRMED"]
    rejected = [b for b in decided.values() if b["status"] == "REJECTED"]

    assert len(confirmed) == 1, f"double spend: {len(confirmed)} bookings confirmed"
    assert len(rejected) == n - 1
    assert all(b.get("reason") == "INSUFFICIENT_FUNDS" for b in rejected)

    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 0,
               what="balance exactly spent, never negative")
    # compensation: every losing booking released the seat it briefly held
    wait_until(lambda: len(seats_taken(client, fid)) == 1,
               what="exactly one seat held; all losing holds released")


def test_hold_expiry_frees_seat(client):
    """A booking holds a seat but cannot complete (no funds). After the
    hold-timeout the seat auto-expires and is bookable again by a fresh booking.
    No seat is permanently leaked."""
    price = 1_000
    f = create_flight(client, seat_count=10, seat_price=price)
    fid = f["id"]
    seat = seat_ids(f)[0]
    broke = create_customer(client, balance=0)   # cannot pay -> hold must be released

    stuck = new_booking_id()
    book_seat(client, stuck, broke, fid, seat)
    b = wait_decided(client, stuck)
    assert b["status"] == "REJECTED", b

    # The seat must become available again (compensation, possibly via hold expiry).
    # Poll past the hold-timeout; LONG_DEADLINE covers a few-second timeout.
    wait_until(lambda: seat_state(client, fid, seat)["available"],
               deadline=LONG_DEADLINE, what="seat freed after failed hold (no leak)")

    # a fresh, funded booking can now take the same seat
    rich = create_customer(client, balance=price * 2)
    fresh = new_booking_id()
    book_seat(client, fresh, rich, fid, seat)
    nb = wait_decided(client, fresh, deadline=LONG_DEADLINE)
    assert nb["status"] == "CONFIRMED", nb
    wait_until(lambda: seat_state(client, fid, seat).get("bookingId") == fresh,
               deadline=LONG_DEADLINE,
               what="seat rebookable after the previous hold expired")


def test_saga_compensation_no_residual_charge(client):
    """Payment fails mid-flow (no funds): seat released, booking REJECTED, and
    NO residual charge anywhere."""
    price = 5_000
    f = create_flight(client, seat_count=10, seat_price=price)
    fid = f["id"]
    seat = seat_ids(f)[1]
    cid = create_customer(client, balance=price - 1)   # one cent short
    bid = new_booking_id()

    book_seat(client, bid, cid, fid, seat)
    b = wait_decided(client, bid)
    assert b["status"] == "REJECTED", b
    assert b.get("reason") == "INSUFFICIENT_FUNDS", b

    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == price - 1,
               what="no residual charge after compensation")
    wait_until(lambda: seat_state(client, fid, seat)["available"],
               deadline=LONG_DEADLINE, what="held seat released after compensation")


def test_exactly_once_notifications_under_load(client):
    """A burst of B bookings (mixed outcomes): exactly one notification per
    decision — never zero, never two — under concurrent processing."""
    b = 20
    price = 200
    affordable = rng.randint(8, 12)
    f = create_flight(client, seat_count=b, seat_price=price)  # each booking a distinct seat
    fid = f["id"]
    seats = seat_ids(f)
    cid = create_customer(client, balance=affordable * price)  # only some succeed

    bids = [new_booking_id() for _ in range(b)]
    parallel([
        (lambda bb=bb, s=s: book_seat(client, bb, cid, fid, s))
        for bb, s in zip(bids, seats)
    ])
    decided = wait_all_decided(client, bids)
    assert len(decided) == b

    def exactly_one_each() -> bool:
        ns = notifications_for(client, cid)
        counts = {bid: 0 for bid in bids}
        for nt in ns:
            if nt["bookingId"] in counts:
                counts[nt["bookingId"]] += 1
        assert all(c <= 1 for c in counts.values()), f"duplicate notifications: {counts}"
        return all(c == 1 for c in counts.values())

    wait_until(exactly_one_each, deadline=LONG_DEADLINE,
               what="exactly one notification per decided booking")


def test_status_never_regresses_and_conservation(client):
    """Causal ordering + conservation under a mixed burst:
    - a booking observed CONFIRMED/REJECTED never goes back to PENDING,
    - confirmed+rejected deltas equal the burst size,
    - revenue delta == seatPrice * confirmed == customer balance decrease,
    - seats sold == seats shown taken (no double-book, no leak)."""
    before = stats(client)
    b = 15
    price = 200
    seat_count = rng.randint(5, 9)
    f = create_flight(client, seat_count=seat_count, seat_price=price)
    fid = f["id"]
    seats = seat_ids(f)
    start_balance = b * price * 2
    cid = create_customer(client, balance=start_balance)

    # round-robin over the limited seats so seats (not money) bound confirmations
    bids = [new_booking_id() for _ in range(b)]
    parallel([
        (lambda bb=bb, i=i: book_seat(client, bb, cid, fid, seats[i % seat_count]))
        for i, bb in enumerate(bids)
    ])

    # sample statuses while processing: any decided booking must stay decided
    seen_decided: dict[str, str] = {}
    import time
    end = time.monotonic() + DEADLINE
    while time.monotonic() < end:
        for bid in bids:
            r = client.get(f"/bookings/{bid}")
            if r.status_code != 200:
                continue
            st = r.json()["status"]
            if bid in seen_decided:
                assert st == seen_decided[bid], \
                    f"status regression on {bid}: {seen_decided[bid]} -> {st}"
            elif st in ("CONFIRMED", "REJECTED"):
                seen_decided[bid] = st
        if len(seen_decided) == b:
            break
        time.sleep(0.15)

    decided = wait_all_decided(client, bids)
    n_confirmed = sum(1 for x in decided.values() if x["status"] == "CONFIRMED")
    n_rejected = b - n_confirmed
    revenue_expected = n_confirmed * price

    def conserved() -> bool:
        s = stats(client)
        balance = get_json(client, f"/customers/{cid}")["balance"]
        taken = len(seats_taken(client, fid))
        return (s["confirmed"] - before["confirmed"] == n_confirmed
                and s["rejected"] - before["rejected"] == n_rejected
                and s["revenue"] - before["revenue"] == revenue_expected
                and start_balance - balance == revenue_expected
                and taken == n_confirmed)

    wait_until(conserved, deadline=LONG_DEADLINE,
               what="conservation: stats == money moved == seats sold == seats taken")
