def test_pair_success(client):
    r = client.post("/api/pair", json={"pin": "123456"})
    assert r.status_code == 200
    body = r.json()
    assert body["device_token"]
    assert body["device_id"]


def test_pair_wrong_pin(client):
    r = client.post("/api/pair", json={"pin": "000000"})
    assert r.status_code == 401


def test_pair_rate_limit_kicks_in_after_repeated_wrong_pins(client):
    for _ in range(5):
        r = client.post("/api/pair", json={"pin": "000000"})
        assert r.status_code == 401

    r = client.post("/api/pair", json={"pin": "000000"})
    assert r.status_code == 429

    # Rate limit blocks the source regardless of PIN correctness once tripped.
    r = client.post("/api/pair", json={"pin": "123456"})
    assert r.status_code == 429
