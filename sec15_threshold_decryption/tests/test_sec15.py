"""SEC-15 acceptance tests - one section per acceptance criterion."""

import itertools

import pytest

from sec15 import shamir
from sec15.app import create_app
from sec15.election import Election, PollStillOpen
from sec15.election_keys import key_ceremony


@pytest.fixture(scope="module")
def ceremony():
    """Generating an RSA-3072 key takes a moment, so do it once for the file."""
    return key_ceremony()


@pytest.fixture
def election(ceremony):
    keys, _ = ceremony
    e = Election(keys)
    for choice in ["Alice", "Bob", "Alice"]:
        e.cast({"choice": choice})
    return e


@pytest.fixture
def shares(ceremony):
    return ceremony[1]


# -------------------------------------------------- Criterion 1: 2 shares fail
def test_decryption_with_2_shares_fails(election, shares):
    election.close_poll()
    with pytest.raises(shamir.ShareError, match="At least 3"):
        election.decrypt_all(shares[:2])


def test_same_share_twice_does_not_count_as_two(election, shares):
    election.close_poll()
    with pytest.raises(shamir.ShareError):
        election.decrypt_all([shares[0], shares[0], shares[1]])


# ------------------------------------- Criterion 2: any 3 of 5 shares succeed
def test_decryption_with_3_of_5_shares_succeeds(election, shares):
    election.close_poll()
    ballots = election.decrypt_all([shares[0], shares[2], shares[4]])
    assert sorted(b["choice"] for b in ballots) == ["Alice", "Alice", "Bob"]


def test_every_combination_of_3_shares_works(election, shares):
    election.close_poll()
    for combo in itertools.combinations(shares, 3):  # all 10 combinations
        assert len(election.decrypt_all(list(combo))) == 3


def test_shares_from_a_different_election_are_rejected(election):
    election.close_poll()
    _, other_shares = key_ceremony()
    with pytest.raises(shamir.ShareError):
        election.decrypt_all(other_shares[:3])


# ---------------------------- Criterion 3: before close of poll -> 423 Locked
def test_decryption_before_close_is_blocked_even_with_all_5_shares(election, shares):
    with pytest.raises(PollStillOpen):
        election.decrypt_all(shares)


def test_http_decrypt_before_close_returns_423(election, shares):
    client = create_app(election, shares).test_client()
    response = client.post("/tally/decrypt", json={"shares": shares})
    assert response.status_code == 423


def test_http_full_flow(election, shares):
    client = create_app(election, shares).test_client()
    assert client.post("/tally/decrypt", json={"shares": shares[:3]}).status_code == 423
    client.post("/poll/close")
    assert client.post("/tally/decrypt", json={"shares": shares[:2]}).status_code == 403
    response = client.post("/tally/decrypt", json={"shares": shares[2:]})
    assert response.status_code == 200 and response.get_json()["count"] == 3


# ------------------------------------------------------- Shamir unit tests
def test_shamir_round_trip_and_typo_detection():
    secret = bytes(range(32))
    pieces = shamir.split(secret, 3, 5)
    assert shamir.combine(pieces[1:4], 3) == secret
    text = pieces[0].to_text()
    typo = text[:10] + ("0" if text[10] != "0" else "1") + text[11:]
    with pytest.raises(shamir.ShareError, match="checksum"):
        shamir.Share.from_text(typo)


def test_private_key_is_never_stored_unencrypted(ceremony):
    keys, _ = ceremony
    assert b"ENCRYPTED PRIVATE KEY" in keys.locked_private_key_pem