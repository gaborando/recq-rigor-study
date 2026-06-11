# T2 Evolution Task — Item Deletion

Extend the existing system (your own previous implementation) with **item
deletion**. The T2 acceptance suite (a superset of the original suite) is the
requirement.

## Behavior

- `DELETE /lists/{id}/items/{itemId}` → `202` (idempotent; deleting an
  already-deleted / never-existing item of a known list is harmless and still
  `202`-accepted; `404` is also acceptable for a never-existing item).
- Deletion removes the item from the list. `GET /lists/{id}` no longer returns
  it, and `totalItems` / `checkedItems` stats decrease accordingly.
- **Completion on deletion (the evolution that stresses the rule):** removing
  the last **unchecked** item can make every remaining item checked. When it
  does — and at least one item remains — the list completes ACTIVE → COMPLETED
  **exactly once**, exactly as a final check would. The completion logic must
  now trigger on deletion too, not only on check.
- Deleting an item from a **COMPLETED** list (all remaining still checked) keeps
  it COMPLETED; no spurious second completion notification.
- Deleting the **last** item (the list becomes empty) leaves the list
  **ACTIVE** — an itemless list is never COMPLETED, so a previously COMPLETED
  list that loses all its items returns to ACTIVE.
- `GET /stats/lists` counts (`active`, `completed`, `totalItems`,
  `checkedItems`) stay conserved at convergence (10 s deadline) after deletions.

## Concurrency scenarios added by the T2 suite

1. N concurrent `DELETE` calls on one item → the item is removed exactly once;
   stats decrease by exactly one item; no spurious completion notification.
2. A list with the last two unchecked items: concurrently **delete** one and
   **check** the other → the list completes ACTIVE → COMPLETED with exactly one
   completion notification (never two, never zero).
3. Conservation audit across a burst of mixed add/check/delete traffic:
   `completed == lists with >=1 item all checked`, `checkedItems` and
   `totalItems` conserved.

## Delivery requirements

Unchanged from SPEC.md. Modify your existing codebase in place; the diff
between your starting tree and your final tree is part of the measurement.
