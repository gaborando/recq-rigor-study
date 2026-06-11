"""Anti-gaming variant suite — NEVER enters an agent workspace.

Re-grades the final artifact on the same invariants with different shapes:
different list sizes, more racers, interleaved uncheck/recheck, mixed traffic.
An implementation that hard-coded the acceptance suite's literal patterns fails
here.

Run from the repo root:  pytest variant/todolist
(the local conftest re-exports the acceptance suite's fixtures and helpers)
"""

from conftest import (
    LONG_DEADLINE,
    add_item,
    check_item,
    completion_count,
    create_list,
    items_of,
    list_status,
    list_view,
    make_list_with_items,
    parallel,
    rng,
    stats,
    uncheck_item,
    wait_status,
    wait_until,
)


def test_concurrent_final_check_large_list(client):
    """Variant: a larger list, most items pre-checked, many racers finishing the
    last few at once -> exactly one completion."""
    total = rng.randint(8, 14)
    racers = rng.randint(3, 5)
    pre = total - racers
    lid, iids = make_list_with_items(client, total, checked=pre)
    last = iids[pre:]

    responses = parallel([(lambda i=i: check_item(client, lid, i)) for i in last])
    assert all(r.status_code in (200, 202) for r in responses)

    wait_status(client, lid, "COMPLETED")

    def exactly_one() -> bool:
        c = completion_count(client, lid)
        assert c <= 1, f"duplicate completions: {c}"
        return c == 1

    wait_until(exactly_one, what="one completion on a large list with many racers")


def test_uncheck_recheck_storm_counts_transitions(client):
    """Variant: repeatedly uncheck-then-recheck the final item; each full
    re-completion is a distinct notification, no duplicates per transition."""
    lid, iids = make_list_with_items(client, 2)
    other, toggling = iids[0], iids[1]
    check_item(client, lid, other)

    transitions = 0
    rounds = rng.randint(2, 4)
    for _ in range(rounds):
        check_item(client, lid, toggling)           # completes
        transitions += 1
        wait_status(client, lid, "COMPLETED")
        wait_until(lambda t=transitions: completion_count(client, lid) == t,
                   what=f"completion transition #{transitions}")
        uncheck_item(client, lid, toggling)          # back to ACTIVE
        wait_status(client, lid, "ACTIVE")

    wait_until(lambda: completion_count(client, lid) == transitions,
               what="notification count == number of completion transitions")


def test_duplicate_toggle_storm_heavier(client):
    """Variant: heavier duplicate-check storm on the final item of a list."""
    m = 16
    lid, iids = make_list_with_items(client, 1)
    iid = iids[0]
    parallel([(lambda: check_item(client, lid, iid)) for _ in range(m)])

    wait_status(client, lid, "COMPLETED")
    wait_until(lambda: completion_count(client, lid) == 1,
               what="single completion under heavy check storm")


def test_mixed_interleaved_traffic_conservation(client):
    """Adds, checks, and unchecks interleaved concurrently across many lists;
    audit conservation against the views' own ground truth."""
    before = stats(client)
    b = 8
    lists: list[tuple[str, list[str]]] = [make_list_with_items(client, 2, checked=1)
                                          for _ in range(b)]

    ops = []
    for lid, iids in lists:
        ops.append((lambda l=lid, i=iids[1]: check_item(client, l, i)))   # may complete
        ops.append((lambda l=lid: add_item(client, l)))                   # adds unchecked
        ops.append((lambda l=lid, i=iids[0]: uncheck_item(client, l, i))) # may un-complete
    rng.shuffle(ops)
    parallel(ops, max_workers=12)

    def conserved() -> bool:
        completed_truth = 0
        total_items = 0
        checked_items = 0
        for lid, _ in lists:
            v = list_view(client, lid)
            if v is None:
                return False
            items = v["items"]
            total_items += len(items)
            checked_items += sum(1 for it in items if it["checked"])
            all_checked = len(items) > 0 and all(it["checked"] for it in items)
            if all_checked:
                completed_truth += 1
                if v["status"] != "COMPLETED":
                    return False
            elif v["status"] != "ACTIVE":
                return False
        s = stats(client)
        return (s["totalItems"] - before["totalItems"] == total_items
                and s["checkedItems"] - before["checkedItems"] == checked_items
                and s["completed"] - before["completed"] == completed_truth)

    wait_until(conserved, deadline=LONG_DEADLINE,
               what="conservation under interleaved mixed traffic")
