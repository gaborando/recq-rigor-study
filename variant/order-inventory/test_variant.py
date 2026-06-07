"""Anti-gaming variant suite — NEVER enters an agent workspace.

Re-grades the final artifact on the same invariants with different shapes:
different cardinalities, quantities > 1, interleaved mixed traffic. An
implementation that hard-coded the acceptance suite's literal patterns fails here.

Run from the repo root:  pytest variant/order-inventory
(the local conftest re-exports the acceptance suite's fixtures and helpers)
"""

from conftest import (
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
    wait_until,
    LONG_DEADLINE,
)


def test_oversell_race_multi_quantity(client):
    """Variant: quantities > 1 racing for limited stock; conservation on units."""
    stock = 10
    price = 70
    pid = create_product(client, unit_price=price, stock=stock)
    cid = create_customer(client, balance=1_000_000)

    quantities = [rng.randint(2, 4) for _ in range(8)]
    oids = [new_order_id() for _ in quantities]
    parallel([
        (lambda o=o, q=q: place_order(client, o, cid, pid, quantity=q))
        for o, q in zip(oids, quantities)
    ])
    decided = wait_all_decided(client, oids)

    sold = sum(decided[o]["quantity"] for o in oids if decided[o]["status"] == "CONFIRMED")
    assert sold <= stock, f"oversold: {sold} units from stock {stock}"

    def units_conserved() -> bool:
        return get_json(client, f"/products/{pid}")["stock"] == stock - sold
    wait_until(units_conserved, what="unit conservation with multi-quantity orders")


def test_duplicate_storm_with_more_replicas(client):
    m = 16  # heavier retry storm than the acceptance suite
    pid = create_product(client, unit_price=333, stock=5)
    cid = create_customer(client, balance=999)
    oid = new_order_id()
    parallel([(lambda: place_order(client, oid, cid, pid, quantity=1)) for _ in range(m)])

    wait_until(lambda: get_json(client, f"/orders/{oid}")["status"] == "CONFIRMED",
               what="order decided")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 999 - 333,
               what="single charge under heavy retry storm")
    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 4,
               what="single stock decrement under heavy retry storm")
    wait_until(lambda: sum(1 for n in notifications_for(client, cid)
                           if n["orderId"] == oid) == 1,
               what="single notification under heavy retry storm")


def test_mixed_interleaved_traffic_conservation(client):
    """Restocks, deposits, and orders interleaved concurrently; audit conservation."""
    before = stats(client)
    price = 110
    pid = create_product(client, unit_price=price, stock=6)
    cid = create_customer(client, balance=8 * price)

    oids = [new_order_id() for _ in range(10)]
    ops = (
        [(lambda o=o: place_order(client, o, cid, pid, quantity=1)) for o in oids]
        + [(lambda: client.post(f"/products/{pid}/restock", json={"units": 1}))
           for _ in range(4)]
        + [(lambda: client.post(f"/customers/{cid}/deposit", json={"amount": price}))
           for _ in range(2)]
    )
    rng.shuffle(ops)
    parallel(ops, max_workers=8)

    decided = wait_all_decided(client, oids)
    n_confirmed = sum(1 for o in decided.values() if o["status"] == "CONFIRMED")

    def conserved() -> bool:
        s = stats(client)
        p = get_json(client, f"/products/{pid}")
        c = get_json(client, f"/customers/{cid}")
        money_in = 8 * price + 2 * price
        units_in = 6 + 4
        return (
            s["confirmed"] - before["confirmed"] == n_confirmed
            and s["revenue"] - before["revenue"] == n_confirmed * price
            and p["stock"] == units_in - n_confirmed          # units conserved
            and c["balance"] == money_in - n_confirmed * price  # money conserved
        )

    wait_until(conserved, deadline=LONG_DEADLINE,
               what="conservation under interleaved mixed traffic")
