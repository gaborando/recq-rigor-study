"""Order lifecycle: confirmation, rejection paths, compensation, notifications, stats."""

from conftest import (
    create_customer,
    create_product,
    get_json,
    new_order_id,
    notifications_for,
    place_order,
    stats,
    wait_decided,
    wait_until,
)


def test_happy_path_confirmed(client):
    pid = create_product(client, unit_price=300, stock=10)
    cid = create_customer(client, balance=5_000)
    oid = new_order_id()

    r = place_order(client, oid, cid, pid, quantity=2)
    assert r.status_code in (200, 202), r.text[:200]

    o = wait_decided(client, oid)
    assert o["status"] == "CONFIRMED", o
    assert o.get("total") == 600

    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 8,
               what="stock decremented exactly once")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 4_400,
               what="balance charged exactly once")
    wait_until(lambda: any(n["orderId"] == oid and n["status"] == "CONFIRMED"
                           for n in notifications_for(client, cid)),
               what="confirmation notification")


def test_out_of_stock_rejected_nothing_charged(client):
    pid = create_product(client, unit_price=300, stock=1)
    cid = create_customer(client, balance=5_000)
    oid = new_order_id()

    place_order(client, oid, cid, pid, quantity=5)
    o = wait_decided(client, oid)
    assert o["status"] == "REJECTED", o
    assert o.get("reason") == "OUT_OF_STOCK", o

    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 1,
               what="stock untouched")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 5_000,
               what="nothing charged")


def test_insufficient_funds_rejected_with_compensation(client):
    """The saga path: stock may be reserved first, then must be RELEASED."""
    pid = create_product(client, unit_price=1_000, stock=4)
    cid = create_customer(client, balance=500)   # cannot afford even one unit
    oid = new_order_id()

    place_order(client, oid, cid, pid, quantity=1)
    o = wait_decided(client, oid)
    assert o["status"] == "REJECTED", o
    assert o.get("reason") == "INSUFFICIENT_FUNDS", o

    # compensation: any transient reservation is released; no residual effect
    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 4,
               what="reservation released (compensation)")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 500,
               what="no money taken")
    wait_until(lambda: any(n["orderId"] == oid and n["status"] == "REJECTED"
                           for n in notifications_for(client, cid)),
               what="rejection notification")


def test_stats_reflect_decisions(client):
    before = stats(client)
    pid = create_product(client, unit_price=250, stock=10)
    cid = create_customer(client, balance=250)   # affords exactly one unit

    ok, ko = new_order_id(), new_order_id()
    place_order(client, ok, cid, pid, quantity=1)     # -> CONFIRMED
    wait_decided(client, ok)
    place_order(client, ko, cid, pid, quantity=1)     # -> REJECTED (funds gone)
    wait_decided(client, ko)

    def converged() -> bool:
        s = stats(client)
        return (s["confirmed"] - before["confirmed"] == 1
                and s["rejected"] - before["rejected"] == 1
                and s["revenue"] - before["revenue"] == 250)

    wait_until(converged, what="stats convergence")
