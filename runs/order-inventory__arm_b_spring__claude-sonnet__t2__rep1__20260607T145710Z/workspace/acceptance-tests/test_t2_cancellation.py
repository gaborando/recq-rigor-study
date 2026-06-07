"""T2 evolution suite: order cancellation (see T2_FEATURE.md).

Only collected when TASK=t2 — T1 runs are not graded against this file.
"""

import os

import pytest

from conftest import (
    create_customer,
    create_product,
    get_json,
    new_order_id,
    notifications_for,
    parallel,
    place_order,
    stats,
    wait_decided,
    wait_until,
)

pytestmark = pytest.mark.skipif(
    os.environ.get("TASK", "t1") != "t2",
    reason="T2 evolution suite (set TASK=t2)",
)


def _confirmed_order(client, *, price=500, stock=10, balance=5_000, qty=2):
    pid = create_product(client, unit_price=price, stock=stock)
    cid = create_customer(client, balance=balance)
    oid = new_order_id()
    place_order(client, oid, cid, pid, quantity=qty)
    o = wait_decided(client, oid)
    assert o["status"] == "CONFIRMED", o
    return pid, cid, oid, price * qty, qty


def test_cancel_refunds_and_restores_exactly_once(client):
    pid, cid, oid, total, qty = _confirmed_order(client)
    balance_after_buy = 5_000 - total
    stock_after_buy = 10 - qty
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == balance_after_buy,
               what="charge applied before cancelling")

    r = client.post(f"/orders/{oid}/cancel")
    assert r.status_code in (200, 202), r.text[:200]

    wait_until(lambda: (get_json(client, f"/orders/{oid}")["status"] == "CANCELLED"),
               what="order cancelled")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"]
               == balance_after_buy + total, what="refund applied exactly once")
    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"]
               == stock_after_buy + qty, what="stock restored exactly once")
    wait_until(lambda: sum(1 for n in notifications_for(client, cid)
                           if n["orderId"] == oid and n["status"] == "CANCELLED") == 1,
               what="exactly one CANCELLED notification")


def test_concurrent_cancels_refund_once(client):
    """N concurrent cancels of one CONFIRMED order: exactly one refund."""
    n = 8
    pid, cid, oid, total, qty = _confirmed_order(client)
    balance_after_buy = 5_000 - total

    responses = parallel([(lambda: client.post(f"/orders/{oid}/cancel")) for _ in range(n)])
    assert all(r.status_code in (200, 202, 409) for r in responses)

    wait_until(lambda: get_json(client, f"/orders/{oid}")["status"] == "CANCELLED",
               what="order cancelled")

    def refunded_exactly_once() -> bool:
        bal = get_json(client, f"/customers/{cid}")["balance"]
        assert bal <= balance_after_buy + total, f"double refund: balance {bal}"
        return bal == balance_after_buy + total

    wait_until(refunded_exactly_once, what="exactly one refund despite concurrent cancels")
    wait_until(lambda: sum(1 for nt in notifications_for(client, cid)
                           if nt["orderId"] == oid and nt["status"] == "CANCELLED") == 1,
               what="exactly one CANCELLED notification")


def test_cancel_non_confirmed_is_noop(client):
    pid = create_product(client, unit_price=1_000, stock=5)
    cid = create_customer(client, balance=100)         # will be REJECTED
    oid = new_order_id()
    place_order(client, oid, cid, pid, quantity=1)
    o = wait_decided(client, oid)
    assert o["status"] == "REJECTED"

    r = client.post(f"/orders/{oid}/cancel")
    assert r.status_code in (200, 202, 409)
    # state unchanged
    wait_until(lambda: get_json(client, f"/orders/{oid}")["status"] == "REJECTED",
               what="rejected order stays rejected")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 100,
               what="no refund for a never-charged order")


def test_stats_gain_cancelled_and_revenue_shrinks(client):
    before = stats(client)
    assert "cancelled" in before, "stats must expose a 'cancelled' count in T2"
    pid, cid, oid, total, qty = _confirmed_order(client)

    def confirmed_counted() -> bool:
        s = stats(client)
        return s["confirmed"] - before["confirmed"] == 1
    wait_until(confirmed_counted, what="confirmation counted")

    client.post(f"/orders/{oid}/cancel")

    def cancelled_counted() -> bool:
        s = stats(client)
        return (s["cancelled"] - before["cancelled"] == 1
                and s["revenue"] - before["revenue"] == 0)  # revenue excludes cancelled
    wait_until(cancelled_counted, what="cancellation reflected in stats incl. revenue")
