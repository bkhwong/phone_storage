import pytest


@pytest.mark.parametrize("client", [{"PAIR_PIN_REUSABLE": "false"}], indirect=True)
def test_second_pair_attempt_is_rejected_when_pin_not_reusable(client):
    r = client.post("/api/pair", json={"pin": "123456"})
    assert r.status_code == 200

    r = client.post("/api/pair", json={"pin": "123456"})
    assert r.status_code == 409


@pytest.mark.parametrize("client", [{"PAIR_PIN_REUSABLE": "false"}], indirect=True)
def test_wrong_pin_still_rejected_before_reusable_check(client):
    r = client.post("/api/pair", json={"pin": "123456"})
    assert r.status_code == 200

    r = client.post("/api/pair", json={"pin": "000000"})
    assert r.status_code == 401
