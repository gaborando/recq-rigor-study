"""Distributed-systems concurrency scenarios. These are part of the contract.

The heart of this domain is EXACTLY-ONCE list completion under a concurrent
final check. Each test races real HTTP requests and asserts system INVARIANTS
(exactly-once completion, exactly-once notifications, no lost update,
conservation) rather than any particular interleaving.
"""

import time

from conftest import (
    DEADLINE,
    LONG_DEADLINE,
    add_item,
    check_item,
    completion_count,
    create_list,
    items_of,
    list_status,
    list_view,
    make_list_with_items,
    notifications_for,
    parallel,
    rename_item,
    rng,
    stats,
    uncheck_item,
    wait_status,
    wait_until,
)


def test_concurrent_final_check_completes_exactly_once(client):
    """THE showcase: a list whose last 2 items are unchecked; two clients check
    them simultaneously -> the list ends COMPLETED with EXACTLY ONE completion
    notification (never two, never zero)."""
    lid, iids = make_list_with_items(client, 2)  # both unchecked
    a, b = iids

    responses = parallel([
        (lambda: check_item(client, lid, a)),
        (lambda: check_item(client, lid, b)),
    ])
    assert all(r.status_code in (200, 202) for r in responses)

    wait_status(client, lid, "COMPLETED")

    def exactly_one() -> bool:
        c = completion_count(client, lid)
        assert c <= 1, f"duplicate completion notifications: {c}"
        return c == 1

    wait_until(exactly_one, what="exactly one completion under concurrent final check")


def test_concurrent_final_check_many_racers(client):
    """Generalised: the last K items unchecked, K racers check them at once ->
    one completion exactly. Repeats to shake out timing-dependent bugs."""
    for _ in range(3):
        k = rng.randint(3, 6)
        already = rng.randint(0, 2)
        lid, iids = make_list_with_items(client, k + already, checked=already)
        last_k = iids[already:]

        responses = parallel([
            (lambda i=i: check_item(client, lid, i)) for i in last_k
        ])
        assert all(r.status_code in (200, 202) for r in responses)

        wait_status(client, lid, "COMPLETED")

        def exactly_one(_lid=lid) -> bool:
            c = completion_count(client, _lid)
            assert c <= 1, f"duplicate completions on {_lid}: {c}"
            return c == 1

        wait_until(exactly_one, what=f"one completion with {k} racers")


def test_duplicate_toggle_idempotency(client):
    """The same check request sent M times concurrently (retry storm): the item
    ends checked once, and if it was the final item there is no duplicate
    completion."""
    m = 8
    lid, iids = make_list_with_items(client, 1)
    iid = iids[0]

    responses = parallel([(lambda: check_item(client, lid, iid)) for _ in range(m)])
    assert all(r.status_code in (200, 202) for r in responses)

    wait_status(client, lid, "COMPLETED")
    wait_until(lambda: all(it["checked"] for it in items_of(client, lid)),
               what="item checked")

    def exactly_one() -> bool:
        c = completion_count(client, lid)
        assert c <= 1, f"duplicate completion under retry storm: {c}"
        return c == 1

    wait_until(exactly_one, what="exactly one completion despite duplicate toggles")


def test_concurrent_rename_and_check_no_lost_update(client):
    """Rename an item while concurrently checking it -> BOTH effects applied,
    no lost update, status consistent."""
    lid, iids = make_list_with_items(client, 2)
    target = iids[0]
    new_content = "renamed-under-race"

    parallel([
        (lambda: rename_item(client, lid, target, new_content)),
        (lambda: check_item(client, lid, target)),
    ])

    def both_applied() -> bool:
        items = {it["itemId"]: it for it in items_of(client, lid)}
        it = items.get(target)
        return it is not None and it["content"] == new_content and it["checked"] is True

    wait_until(both_applied, what="rename AND check both survive (no lost update)")
    # the other item is still unchecked, so the list must still be ACTIVE
    assert list_status(client, lid) == "ACTIVE"


def test_check_uncheck_race_around_completion(client):
    """Concurrent check of the last item and uncheck of another around the
    completion boundary -> at convergence the list is COMPLETED iff all items
    are checked, and notification count equals the number of completion
    transitions (no spurious duplicates)."""
    lid, iids = make_list_with_items(client, 3, checked=1)  # iids[0] checked
    checked0, unchecked1, unchecked2 = iids

    # race: check the two unchecked items AND uncheck the already-checked one
    parallel([
        (lambda: check_item(client, lid, unchecked1)),
        (lambda: check_item(client, lid, unchecked2)),
        (lambda: uncheck_item(client, lid, checked0)),
    ])

    def consistent() -> bool:
        items = items_of(client, lid)
        if len(items) != 3:
            return False
        all_checked = all(it["checked"] for it in items)
        status = list_status(client, lid)
        # completed iff all items checked (with >=1 item)
        if all_checked:
            return status == "COMPLETED"
        return status == "ACTIVE"

    wait_until(consistent, deadline=LONG_DEADLINE,
               what="status consistent with item states at convergence")

    # notifications never exceed the number of ACTIVE->COMPLETED transitions:
    # this single race can complete at most once, so never more than one.
    assert completion_count(client, lid) <= 1, "spurious duplicate completion notification"


def test_exactly_once_notifications_under_load(client):
    """A burst of completions across many lists -> exactly one notification per
    completed list, never zero, never two."""
    b = 20
    lids: list[str] = []
    for _ in range(b):
        lid, iids = make_list_with_items(client, 2)
        lids.append((lid, iids))

    ops = []
    for lid, iids in lids:
        for iid in iids:
            ops.append((lambda l=lid, i=iid: check_item(client, l, i)))
    rng.shuffle(ops)
    parallel(ops, max_workers=16)

    def all_completed_once() -> bool:
        for lid, _ in lids:
            if list_status(client, lid) != "COMPLETED":
                return False
            c = completion_count(client, lid)
            assert c <= 1, f"duplicate completion on {lid}: {c}"
            if c != 1:
                return False
        return True

    wait_until(all_completed_once, deadline=LONG_DEADLINE,
               what="exactly one notification per completed list under load")


def test_status_never_regresses_and_conservation(client):
    """Causal ordering + conservation under a mixed burst:
    - a list observed COMPLETED stays COMPLETED unless an uncheck happens (none
      here), and
    - completed-count delta equals the number of lists with all items checked,
    - checkedItems / totalItems conserved."""
    before = stats(client)
    b = 15
    sizes = [rng.randint(1, 3) for _ in range(b)]
    lists: list[tuple[str, list[str]]] = []
    for n in sizes:
        lists.append(make_list_with_items(client, n))

    ops = []
    for lid, iids in lists:
        for iid in iids:
            ops.append((lambda l=lid, i=iid: check_item(client, l, i)))
    rng.shuffle(ops)

    def fire():
        parallel(ops, max_workers=16)

    import threading
    t = threading.Thread(target=fire)
    t.start()

    # sample statuses while completing: any list seen COMPLETED must stay COMPLETED
    seen_completed: set[str] = set()
    end = time.monotonic() + DEADLINE
    while time.monotonic() < end:
        for lid, _ in lists:
            st = list_status(client, lid)
            if st == "COMPLETED":
                seen_completed.add(lid)
            elif lid in seen_completed:
                # no uncheck happens in this test -> must not regress
                assert st == "COMPLETED", f"status regression on {lid}: COMPLETED -> {st}"
        if len(seen_completed) == b:
            break
        time.sleep(0.15)
    t.join()

    total_items = sum(sizes)

    def conserved() -> bool:
        s = stats(client)
        return (s["completed"] - before["completed"] == b           # all lists complete
                and s["totalItems"] - before["totalItems"] == total_items
                and s["checkedItems"] - before["checkedItems"] == total_items)

    wait_until(conserved, deadline=LONG_DEADLINE,
               what="conservation: completed == lists all-checked, items conserved")
