"""List lifecycle: completion, un-completion, re-completion, notifications, stats."""

from conftest import (
    add_item,
    check_item,
    completion_count,
    create_list,
    items_of,
    list_status,
    list_view,
    make_list_with_items,
    rename_item,
    stats,
    uncheck_item,
    wait_status,
    wait_until,
)


def test_check_all_items_completes_list_once(client):
    lid, iids = make_list_with_items(client, 3)
    for iid in iids:
        assert check_item(client, lid, iid).status_code in (200, 202)

    wait_status(client, lid, "COMPLETED")
    wait_until(lambda: completion_count(client, lid) == 1,
               what="exactly one completion notification")


def test_partial_check_stays_active(client):
    lid, iids = make_list_with_items(client, 3)
    check_item(client, lid, iids[0])
    check_item(client, lid, iids[1])  # one still unchecked

    # status must remain ACTIVE; give views time to (not) converge to COMPLETED
    def still_active() -> bool:
        return list_status(client, lid) == "ACTIVE" and completion_count(client, lid) == 0

    wait_until(lambda: any(it["itemId"] == iids[1] and it["checked"]
                           for it in items_of(client, lid)),
               what="second check applied")
    assert still_active(), "list completed before the last item was checked"


def test_idempotent_check_is_noop(client):
    lid, iids = make_list_with_items(client, 1)
    iid = iids[0]
    for _ in range(4):
        check_item(client, lid, iid)
    wait_status(client, lid, "COMPLETED")
    wait_until(lambda: completion_count(client, lid) == 1,
               what="one completion despite repeated checks")


def test_uncheck_leaves_completed_then_recompletes_once(client):
    lid, iids = make_list_with_items(client, 2)
    for iid in iids:
        check_item(client, lid, iid)
    wait_status(client, lid, "COMPLETED")
    wait_until(lambda: completion_count(client, lid) == 1, what="first completion")

    # uncheck one item -> back to ACTIVE
    uncheck_item(client, lid, iids[0])
    wait_status(client, lid, "ACTIVE")

    # re-check it -> re-completes, second distinct notification
    check_item(client, lid, iids[0])
    wait_status(client, lid, "COMPLETED")
    wait_until(lambda: completion_count(client, lid) == 2,
               what="exactly two completion transitions")


def test_adding_unchecked_item_to_completed_list_reactivates(client):
    lid, iids = make_list_with_items(client, 1)
    check_item(client, lid, iids[0])
    wait_status(client, lid, "COMPLETED")

    add_item(client, lid)  # a fresh unchecked item
    wait_status(client, lid, "ACTIVE")


def test_rename_does_not_change_status(client):
    lid, iids = make_list_with_items(client, 2)
    for iid in iids:
        check_item(client, lid, iid)
    wait_status(client, lid, "COMPLETED")

    assert rename_item(client, lid, iids[0], "renamed content").status_code in (200, 202)
    wait_until(lambda: any(it["itemId"] == iids[0] and it["content"] == "renamed content"
                           for it in items_of(client, lid)),
               what="rename applied")
    # still completed, still exactly one notification
    assert list_status(client, lid) == "COMPLETED"
    wait_until(lambda: completion_count(client, lid) == 1, what="no spurious notification")


def test_stats_reflect_completion_and_conservation(client):
    before = stats(client)
    lid, iids = make_list_with_items(client, 2)
    check_item(client, lid, iids[0])
    check_item(client, lid, iids[1])
    wait_status(client, lid, "COMPLETED")

    def converged() -> bool:
        s = stats(client)
        return (s["completed"] - before["completed"] == 1
                and s["totalItems"] - before["totalItems"] == 2
                and s["checkedItems"] - before["checkedItems"] == 2)

    wait_until(converged, what="stats convergence after completion")
