"""Contract basics: resource creation, views, validation, unknown ids."""

from conftest import create_customer, create_product, get_json, rand_name, wait_until


def test_create_and_read_product(client):
    pid = create_product(client, unit_price=500, stock=7)
    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 7,
               what="product view")
    p = get_json(client, f"/products/{pid}")
    assert p["unitPrice"] == 500
    assert p["id"] == pid


def test_create_and_read_customer(client):
    cid = create_customer(client, balance=10_000)
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 10_000,
               what="customer view")


def test_unknown_ids_are_404(client):
    assert client.get("/products/does-not-exist-xyz").status_code == 404
    assert client.get("/customers/does-not-exist-xyz").status_code == 404


def test_validation_rejects_malformed(client):
    # missing fields
    assert client.post("/products", json={"name": rand_name("p")}).status_code == 400
    # invalid quantities
    pid = create_product(client, unit_price=100, stock=1)
    cid = create_customer(client, balance=1_000)
    r = client.post("/orders", json={
        "orderId": "not-even-checked", "customerId": cid, "productId": pid, "quantity": 0,
    })
    assert r.status_code == 400
    assert client.post(f"/products/{pid}/restock", json={"units": 0}).status_code == 400
    assert client.post(f"/customers/{cid}/deposit", json={"amount": -5}).status_code == 400


def test_restock_and_deposit_apply(client):
    pid = create_product(client, unit_price=100, stock=2)
    cid = create_customer(client, balance=100)
    assert client.post(f"/products/{pid}/restock", json={"units": 3}).status_code == 202
    assert client.post(f"/customers/{cid}/deposit", json={"amount": 50}).status_code == 202
    wait_until(lambda: get_json(client, f"/products/{pid}")["stock"] == 5, what="restock applied")
    wait_until(lambda: get_json(client, f"/customers/{cid}")["balance"] == 150, what="deposit applied")
