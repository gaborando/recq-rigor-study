"""Distributed-systems concurrency scenarios. These are part of the contract.

Each test races real HTTP requests and asserts system INVARIANTS (conservation,
exactly-once, no lost update, no oversell) rather than any particular interleaving.
"""

from conftest import (
    DEADLINE,
    LONG_DEADLINE,
    create_customer,
    create_product,
    get_json,
    new_order_id,
    notifications_for,
    parallel,
    place_order,
    rng,
    stats,
    wait_all_decided,
    wait_decided,
    wait_until,
)


def test_oversell_race(client):
    """N concurrent orders compete for K < N units: exactly K confirmed,
    stock never oversold, losers cleanly rejected."""
    k = rng.randint(3, 6)
    n = k + 5
    price = 100
    pid = create_product(client, unit_price=price, stock=k)
    cid = create_customer(client, balance=n * price * 2)  # funds are NOT the constraint

    oids = [new_order_id() for _ in range(n)]
    responses = parallel([
        (lambda o=o: place_order(client, o, cid, pid, quantity=1)) for o in oids
    ])
    assert all(r.status_code in (200, 202) for r in responses)

    decided = wait_all_decided(client, oids)
    confirmed = [o for o in decided.values() if o["status"] == "CONFIRMED"]
    rejected = [o for o in decided.values() if o["status"] == "REJECTED"]

    assert len(confirmed) == k, f"expected exactly {k} confirmations, got {len(confirmed)}"
    assert len(rejected) == n - k
    assert all(o.get("reason") == "OUT_OF_STOCK" for o in rejected)

    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 0,
               what="stock exactly depleted, never negative")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"]
               == n * price * 2 - k * price,
               what="charged exactly once per confirmed order")


def test_duplicate_command_idempotency(client):
    """The same orderId submitted M times concurrently (client retry storm):
    at most one order effect — one charge, one stock unit, one notification."""
    m = 8
    pid = create_product(client, unit_price=400, stock=10)
    cid = create_customer(client, balance=4_000)
    oid = new_order_id()

    responses = parallel([
        (lambda: place_order(client, oid, cid, pid, quantity=1)) for _ in range(m)
    ])
    assert all(r.status_code in (200, 202) for r in responses)

    o = wait_decided(client, oid)
    assert o["status"] == "CONFIRMED", o

    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 9,
               what="stock decremented exactly once despite duplicates")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 3_600,
               what="charged exactly once despite duplicates")

    def exactly_one_notification() -> bool:
        ns = [n for n in notifications_for(client, cid) if n["orderId"] == oid]
        assert len(ns) <= 1, f"duplicate notifications: {ns}"
        return len(ns) == 1

    wait_until(exactly_one_notification, what="exactly one notification")


def test_concurrent_restock_no_lost_update(client):
    """N concurrent +1 restocks must ALL be applied (classic lost-update)."""
    n = 20
    pid = create_product(client, unit_price=100, stock=0)
    responses = parallel([
        (lambda: client.post(f"/products/{pid}/restock", json={"units": 1}))
        for _ in range(n)
    ])
    assert all(r.status_code == 202 for r in responses)
    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == n,
               deadline=LONG_DEADLINE, what=f"all {n} restocks applied")


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
    """Customer can afford exactly ONE order; N concurrent orders race for the
    funds: exactly one confirmed, balance ends 0, never negative."""
    n = 6
    price = 900
    pid = create_product(client, unit_price=price, stock=100)  # stock NOT the constraint
    cid = create_customer(client, balance=price)               # affords exactly one

    oids = [new_order_id() for _ in range(n)]
    parallel([(lambda o=o: place_order(client, o, cid, pid, quantity=1)) for o in oids])

    decided = wait_all_decided(client, oids)
    confirmed = [o for o in decided.values() if o["status"] == "CONFIRMED"]
    rejected = [o for o in decided.values() if o["status"] == "REJECTED"]

    assert len(confirmed) == 1, f"double spend: {len(confirmed)} orders confirmed"
    assert len(rejected) == n - 1
    assert all(o.get("reason") == "INSUFFICIENT_FUNDS" for o in rejected)

    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 0,
               what="balance exactly spent")
    # compensation: every losing order released its reservation
    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 99,
               what="exactly one unit sold; all losing reservations released")


def test_exactly_once_notifications_under_load(client):
    """A burst of B orders (mixed outcomes): exactly one notification per
    decision — never zero, never two — under concurrent processing."""
    b = 20
    price = 150
    affordable = rng.randint(8, 12)
    pid = create_product(client, unit_price=price, stock=b * 2)
    cid = create_customer(client, balance=affordable * price)  # only some succeed

    oids = [new_order_id() for _ in range(b)]
    parallel([(lambda o=o: place_order(client, o, cid, pid, quantity=1)) for o in oids])
    decided = wait_all_decided(client, oids)
    assert len(decided) == b

    def exactly_one_each() -> bool:
        ns = notifications_for(client, cid)
        counts = {oid: 0 for oid in oids}
        for nt in ns:
            if nt["orderId"] in counts:
                counts[nt["orderId"]] += 1
        assert all(c <= 1 for c in counts.values()), f"duplicate notifications: {counts}"
        return all(c == 1 for c in counts.values())

    wait_until(exactly_one_each, deadline=LONG_DEADLINE,
               what="exactly one notification per decided order")


def test_status_never_regresses_and_conservation(client):
    """Causal ordering + conservation under a mixed burst:
    - an order observed CONFIRMED/REJECTED never goes back to PENDING,
    - confirmed+rejected deltas equal the burst size,
    - revenue delta == sum of confirmed totals == customer balance decrease."""
    before = stats(client)
    b = 15
    price = 200
    pid = create_product(client, unit_price=price, stock=rng.randint(5, 9))
    start_balance = b * price * 2
    cid = create_customer(client, balance=start_balance)

    oids = [new_order_id() for _ in range(b)]
    parallel([(lambda o=o: place_order(client, o, cid, pid, quantity=1)) for o in oids])

    # sample statuses while processing: any decided order must stay decided
    seen_decided: dict[str, str] = {}
    import time
    end = time.monotonic() + DEADLINE
    while time.monotonic() < end:
        for oid in oids:
            r = client.get(f"/orders/{oid}")
            if r.status_code != 200:
                continue
            st = r.json()["status"]
            if oid in seen_decided:
                assert st == seen_decided[oid], \
                    f"status regression on {oid}: {seen_decided[oid]} -> {st}"
            elif st in ("CONFIRMED", "REJECTED"):
                seen_decided[oid] = st
        if len(seen_decided) == b:
            break
        time.sleep(0.15)

    decided = wait_all_decided(client, oids)
    n_confirmed = sum(1 for o in decided.values() if o["status"] == "CONFIRMED")
    n_rejected = b - n_confirmed
    revenue_expected = n_confirmed * price

    def conserved() -> bool:
        s = stats(client)
        balance = get_json(client, f"/customers/{cid}")["balance"]
        return (s["confirmed"] - before["confirmed"] == n_confirmed
                and s["rejected"] - before["rejected"] == n_rejected
                and s["revenue"] - before["revenue"] == revenue_expected
                and start_balance - balance == revenue_expected)

    wait_until(conserved, deadline=LONG_DEADLINE,
               what="conservation: stats == money moved == stock sold")
