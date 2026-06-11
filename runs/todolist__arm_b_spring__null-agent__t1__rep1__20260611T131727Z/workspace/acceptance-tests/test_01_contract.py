"""Contract basics: resource creation, views, validation, unknown ids."""

from conftest import (
    add_item,
    check_item,
    create_list,
    get_json,
    items_of,
    list_view,
    new_id,
    wait_until,
)


def test_create_and_read_list(client):
    lid = create_list(client)
    wait_until(lambda: (v := list_view(client, lid)) is not None and v["status"] == "ACTIVE",
               what="list view")
    v = list_view(client, lid)
    assert v["listId"] == lid
    assert v["items"] == []


def test_add_items_and_read(client):
    lid = create_list(client)
    iid = add_item(client, lid, content="buy milk")

    def has_item() -> bool:
        items = items_of(client, lid)
        return any(it["itemId"] == iid and it["content"] == "buy milk"
                   and it["checked"] is False for it in items)

    wait_until(has_item, what="item appears unchecked")


def test_unknown_ids_are_404(client):
    assert client.get("/lists/does-not-exist-xyz").status_code == 404
    assert client.get("/lists/does-not-exist-xyz/notifications").status_code == 404
    # check/rename of an item on an unknown list
    assert client.put("/lists/nope/items/also-nope/check").status_code == 404


def test_unknown_item_on_known_list_is_404(client):
    lid = create_list(client)
    wait_until(lambda: list_view(client, lid) is not None, what="list exists")
    assert client.put(f"/lists/{lid}/items/{new_id()}/check").status_code == 404
    assert client.put(f"/lists/{lid}/items/{new_id()}/uncheck").status_code == 404
    assert client.put(f"/lists/{lid}/items/{new_id()}/rename",
                      json={"content": "x"}).status_code == 404


def test_validation_rejects_malformed(client):
    # missing fields on list create
    assert client.post("/lists", json={"name": "no id"}).status_code == 400
    # empty name
    assert client.post("/lists", json={"listId": new_id(), "name": ""}).status_code == 400
    lid = create_list(client)
    wait_until(lambda: list_view(client, lid) is not None, what="list exists")
    # empty content on add
    assert client.post(f"/lists/{lid}/items",
                       json={"itemId": new_id(), "content": ""}).status_code == 400
    # missing itemId on add
    assert client.post(f"/lists/{lid}/items", json={"content": "x"}).status_code == 400


def test_empty_list_is_active_not_completed(client):
    """An itemless list is ACTIVE, never COMPLETED."""
    lid = create_list(client)
    wait_until(lambda: list_view(client, lid) is not None, what="list exists")
    v = list_view(client, lid)
    assert v["status"] == "ACTIVE"
    assert v["items"] == []
