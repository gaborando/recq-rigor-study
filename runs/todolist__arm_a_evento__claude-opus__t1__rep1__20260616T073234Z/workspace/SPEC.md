# Collaborative TodoList System — Specification

You are building a small **collaborative todo-list backend**: lists of items that
many clients edit at the same time. The system is exercised exclusively through
the HTTP API defined here and in `openapi.yaml`. The **acceptance test suite is
the requirement**: your implementation is done when the whole suite passes.

The suite includes concurrency scenarios. They are part of the contract: the
system must behave correctly when many requests arrive at the same time. The
heart of this domain is **exactly-once list completion under a concurrent final
check** — getting it right is the whole point.

## Domain

### List
A list has a `name` and a set of items. The **client supplies the list id**
(UUID) at creation; creating the same list id again is **idempotent** — it must
not create a second list or reset its items, regardless of timing or concurrency.

A list has a `status`:

```
ACTIVE ──> COMPLETED        (the LAST unchecked item becomes checked — or is removed — leaving every remaining item checked, AND at least one item exists)
       <── (an item is unchecked, OR a checked item is added/un-checked) leaving an unchecked item
```

- A list is **COMPLETED** iff it has **at least one item** and **every** item is
  **checked**.
- An **empty list** (no items) is **ACTIVE**, never COMPLETED. Adding no items,
  or removing the last item, leaves the list ACTIVE.
- Status follows the items: it transitions to COMPLETED at the moment the last
  unchecked item becomes checked, and back to ACTIVE the moment an item is
  unchecked (or an unchecked item is added).

### Item
An item belongs to one list, has `content` (a non-empty string) and a `checked`
flag. The **client supplies the item id** (UUID); all item operations are
**idempotent** on `(listId, itemId)`:

- **add** — `POST /lists/{id}/items {itemId, content}`. Re-adding the same item
  id is idempotent (no duplicate item, content unchanged). A newly added item is
  unchecked.
- **rename** — changes `content`. Idempotent.
- **check** / **uncheck** — set/clear `checked`. Checking an already-checked item
  is a no-op; unchecking an already-unchecked item is a no-op.

### List completion (the friction case)
When the **last unchecked item** of a list becomes checked, the list transitions
ACTIVE → COMPLETED **exactly once**. This must hold under a **concurrent final
check**: if two (or more) clients check the last remaining items at the same
time, the list ends COMPLETED with **exactly one** completion effect — never
zero, never two. Detecting "are all items now checked?" and recording the
transition must be one atomic decision per list (a classic check-then-act race
if done naively).

If an item is later unchecked, the list leaves COMPLETED (back to ACTIVE), and
**re-completes exactly once** when every item is checked again. Each ACTIVE →
COMPLETED transition is a distinct completion.

### Notifications
Every list-completion **transition** (ACTIVE → COMPLETED) produces **exactly
one** notification for that list — never zero, never two — under any concurrency
or retry pattern. A list completed, uncompleted, and re-completed yields exactly
two notifications (one per transition).

### Statistics
A statistics view reports, across all lists: `active` count, `completed` count,
`totalItems`, and `checkedItems`. Conservation must hold at convergence:

- `completed == number of lists that have at least one item and all items checked`
- `checkedItems == sum over all lists of (items with checked == true)`

## Consistency model

Reads may be **eventually consistent**: after a write, views (list status,
items, stats, notifications) must **converge within 10 seconds**. The acceptance
tests poll with a deadline; they never require synchronous read-your-writes on
views. Conservation must hold at convergence (see Statistics).

## API summary (normative schema in `openapi.yaml`)

| Method & path | Behavior |
|---|---|
| `POST /lists` | `{listId, name}` → `202 {listId, name, status}`. Idempotent on `listId`. |
| `GET /lists/{id}` | → `200 {listId, name, status, items:[{itemId, content, checked}]}` |
| `POST /lists/{id}/items` | `{itemId, content}` → `202`. Idempotent on `itemId`. Adds an unchecked item. |
| `PUT /lists/{id}/items/{itemId}/check` | → `202`. Idempotent. May complete the list (exactly once). |
| `PUT /lists/{id}/items/{itemId}/uncheck` | → `202`. Idempotent. Leaves COMPLETED if it was. |
| `PUT /lists/{id}/items/{itemId}/rename` | `{content}` → `202`. Idempotent. No lost update vs. check. |
| `GET /lists/{id}/notifications` | → `200 [{listId, status:"COMPLETED", ...}]` — one per completion transition |
| `GET /stats/lists` | → `200 {active, completed, totalItems, checkedItems}` |

Errors: malformed body → `400`; unknown id on GET/PUT/POST-subresource → `404`;
empty `content`, missing required fields → `400`. Operating on an unknown
`itemId` of a known list (check/uncheck/rename of a non-existent item) → `404`.

## Delivery requirements (identical for every implementation)

1. Java 21, Maven, single Spring Boot deployable — start from the provided
   skeleton; do not change its build coordinates or dependency constraints.
2. Root package `com.study.app` with layer sub-packages:
   `web` (HTTP), `command` (write side), `query` (read side / views),
   `domain` (domain model), `config` (wiring). Keep code in its layer.
3. The app must serve HTTP on `${PORT}` (already configured in the skeleton)
   and expose Spring Actuator `/actuator/health` (already configured).
4. Use the provided PostgreSQL (already configured in the skeleton) for any
   persistence you need. Do not add other infrastructure.
5. **Durability (real-world deployment).** All business state — lists, items,
   their checked state, completion status, notifications, statistics — MUST be
   persisted in the provided PostgreSQL. The system MUST recover its state after
   a process restart: stopping and restarting the application (the database
   keeps running) must not lose committed lists, items, checked state, or
   completion transitions. In-memory-only storage (maps, lists, caches as the
   source of truth) is NOT acceptable. This is exercised by a restart-survival
   check.
6. Definition of done: `mvn -B verify` succeeds, the app boots, and the
   acceptance suite in `./acceptance-tests` passes (`make test`).
