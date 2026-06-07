# T2 Evolution Task — Order Cancellation

Extend the existing system (your own previous implementation) with **order
cancellation**. The T2 acceptance suite (a superset of the original suite) is
the requirement.

## Behavior

- `POST /orders/{id}/cancel` → `202` (idempotent; replays are harmless).
- Only a **CONFIRMED** order can become `CANCELLED`. Cancelling a PENDING or
  REJECTED order has no effect (the request is still `202`-accepted; the order
  keeps its state — `409` is also acceptable for those cases).
- Cancellation, **exactly once** regardless of concurrent/repeated requests:
  - refunds the order total to the customer balance,
  - restores the order quantity to product stock,
  - produces exactly one additional notification `{orderId, status: "CANCELLED"}`,
  - sets the order status to `CANCELLED` (a terminal state; no regression).
- `GET /stats/orders` gains a `cancelled` count; `revenue` **excludes**
  cancelled orders (i.e. it is reduced by the cancelled order's total).
- Conservation still holds at convergence (10 s deadline), now including
  refunds and restored stock.

## Concurrency scenarios added by the T2 suite

1. N concurrent `cancel` calls on one CONFIRMED order → exactly one refund,
   one stock restore, one CANCELLED notification.
2. Cancel raced against the order's own processing (cancel sent while PENDING)
   → either the order is never confirmed (and cancel is a no-op) or it is
   confirmed and then cancelled exactly once — never a double refund, never
   lost stock.
3. Conservation audit across a burst of mixed place/cancel traffic.

## Delivery requirements

Unchanged from SPEC.md. Modify your existing codebase in place; the diff
between your starting tree and your final tree is part of the measurement.
