"""T2 evolution suite: item deletion (see T2_FEATURE.md).

Only collected when TASK=t2 — T1 runs are not graded against this file.
"""

import os

import pytest

from conftest import (
    LONG_DEADLINE,
    add_item,
    check_item,
    completion_count,
    create_list,
    delete_item,
    items_of,
    list_status,
    list_view,
    make_list_with_items,
    parallel,
    rng,
    stats,
    wait_status,
    wait_until,
)

pytestmark = pytest.mark.skipif(
    os.environ.get("TASK", "t1") != "t2",
    reason="T2 evolution suite (set TASK=t2)",
)


def _item_ids(client, lid) -> set:
    return {it["itemId"] for it in items_of(client, lid)}


def test_delete_item_is_idempotent(client):
    lid, iids = make_list_with_items(client, 3)
    target = iids[0]
    for _ in range(4):
        assert delete_item(client, lid, target).status_code in (200, 202, 404)

    wait_until(lambda: target not in _item_ids(client, lid), what="item removed")
    wait_until(lambda: len(items_of(client, lid)) == 2,
               what="exactly one item removed despite repeats")


def test_delete_last_unchecked_item_completes_once(client):
    """Deleting the last unchecked item (the rest are checked) completes the
    list ACTIVE -> COMPLETED exactly once."""
    lid, iids = make_list_with_items(client, 3, checked=2)  # iids[2] unchecked
    unchecked = iids[2]
    assert list_status(client, lid) == "ACTIVE"

    assert delete_item(client, lid, unchecked).status_code in (200, 202)

    wait_status(client, lid, "COMPLETED")
    wait_until(lambda: completion_count(client, lid) == 1,
               what="exactly one completion triggered by deletion")


def test_delete_from_completed_list_stays_completed(client):
    lid, iids = make_list_with_items(client, 3)
    for iid in iids:
        check_item(client, lid, iid)
    wait_status(client, lid, "COMPLETED")
    wait_until(lambda: completion_count(client, lid) == 1, what="first completion")

    # remove one (still all-checked remain) -> stays COMPLETED, no new notification
    assert delete_item(client, lid, iids[0]).status_code in (200, 202)
    wait_until(lambda: len(items_of(client, lid)) == 2, what="item removed")
    assert list_status(client, lid) == "COMPLETED"
    wait_until(lambda: completion_count(client, lid) == 1,
               what="no spurious completion notification on delete")


def test_delete_all_items_leaves_active(client):
    lid, iids = make_list_with_items(client, 2)
    for iid in iids:
        check_item(client, lid, iid)
    wait_status(client, lid, "COMPLETED")

    for iid in iids:
        delete_item(client, lid, iid)
    # empty list -> ACTIVE
    wait_until(lambda: items_of(client, lid) == [] and list_status(client, lid) == "ACTIVE",
               what="emptied list returns to ACTIVE")


def test_stats_updated_on_delete(client):
    before = stats(client)
    lid, iids = make_list_with_items(client, 3, checked=2)  # 3 items, 2 checked

    def added() -> bool:
        s = stats(client)
        return (s["totalItems"] - before["totalItems"] == 3
                and s["checkedItems"] - before["checkedItems"] == 2)
    wait_until(added, what="items counted before delete")

    # delete one checked item
    checked_target = iids[0]
    delete_item(client, lid, checked_target)

    def converged() -> bool:
        s = stats(client)
        return (s["totalItems"] - before["totalItems"] == 2
                and s["checkedItems"] - before["checkedItems"] == 1)
    wait_until(converged, what="stats decrease on delete")


def test_concurrent_deletes_remove_once(client):
    """N concurrent DELETEs of one item: removed exactly once; stats off by one;
    no spurious completion notification."""
    n = 8
    lid, iids = make_list_with_items(client, 3)
    target = iids[0]

    responses = parallel([(lambda: delete_item(client, lid, target)) for _ in range(n)])
    assert all(r.status_code in (200, 202, 404) for r in responses)

    wait_until(lambda: target not in _item_ids(client, lid)
               and len(items_of(client, lid)) == 2,
               what="exactly one item removed under concurrent deletes")
    # two unchecked items remain -> still ACTIVE, never completed
    assert list_status(client, lid) == "ACTIVE"
    assert completion_count(client, lid) == 0, "spurious completion on concurrent delete"


def test_concurrent_delete_and_check_complete_once(client):
    """Last two unchecked items: concurrently DELETE one and CHECK the other ->
    the list completes exactly once."""
    for _ in range(3):
        lid, iids = make_list_with_items(client, 3, checked=1)  # iids[0] checked
        del_target, check_target = iids[1], iids[2]

        parallel([
            (lambda: delete_item(client, lid, del_target)),
            (lambda: check_item(client, lid, check_target)),
        ])

        # remaining items (iids[0], iids[2]) are both checked -> COMPLETED
        wait_status(client, lid, "COMPLETED")

        def exactly_one(_lid=lid) -> bool:
            c = completion_count(client, _lid)
            assert c <= 1, f"duplicate completion on {_lid}: {c}"
            return c == 1

        wait_until(exactly_one, what="one completion from delete+check race")


def test_conservation_under_mixed_add_check_delete(client):
    """Burst of mixed add/check/delete across many lists; audit conservation:
    completed == lists with >=1 item all checked, items conserved."""
    before = stats(client)
    b = 10
    lists: list[tuple[str, list[str]]] = [make_list_with_items(client, 3, checked=1)
                                          for _ in range(b)]

    ops = []
    for lid, iids in lists:
        # check one, delete one — leaving one item; outcomes vary per timing
        ops.append((lambda l=lid, i=iids[1]: check_item(client, l, i)))
        ops.append((lambda l=lid, i=iids[2]: delete_item(client, l, i)))
        ops.append((lambda l=lid: add_item(client, l)))
    rng.shuffle(ops)
    parallel(ops, max_workers=16)

    def conserved() -> bool:
        s = stats(client)
        # recompute the ground truth from the views themselves
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
        s2 = stats(client)
        return (s2["totalItems"] - before["totalItems"] == total_items
                and s2["checkedItems"] - before["checkedItems"] == checked_items
                and s2["completed"] - before["completed"] == completed_truth)

    wait_until(conserved, deadline=LONG_DEADLINE,
               what="conservation under mixed add/check/delete")
