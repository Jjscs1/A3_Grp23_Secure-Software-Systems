import copy
import datetime as dt
import json

import pytest

from sec18.publisher import ResultsPublisher
from sec18.signing import signature_is_valid
from sec18.verifier import Rejected, SampleVerifier

RESULTS_V1 = [{"contest": "house", "region": "MELB", "winner": "NGUYEN, Ava"}]
RESULTS_V2 = [{"contest": "house", "region": "MELB", "winner": "SINGH, Maya"}]


@pytest.fixture
def commission():
    return ResultsPublisher()


@pytest.fixture
def outlet(commission):
    return SampleVerifier(commission.public_key_pem)


#Criterion 1: results payload signature verifies with the published public key
def test_signature_verifies_with_published_public_key(commission):
    publication = commission.publish(1, RESULTS_V1)
    assert signature_is_valid(
        commission.public_key_pem, publication["payload"], publication["signature"]
    )


def test_verifier_accepts_a_genuine_payload(commission, outlet):
    payload = outlet.verify(commission.publish(1, RESULTS_V1))
    assert payload["sequence"] == 1 and payload["results"] == RESULTS_V1


def test_payload_carries_sequence_timestamp_and_key_id(commission):
    payload = commission.publish(1, RESULTS_V1)["payload"]
    assert payload["sequence"] == 1
    assert dt.datetime.fromisoformat(payload["issued_at"]).tzinfo is not None
    assert payload["key_id"] == commission.key_id


def test_still_verifies_after_being_sent_as_json(commission, outlet):
    """A media outlet receives text over the network, not Python objects."""
    received = json.loads(json.dumps(commission.publish(1, RESULTS_V1)))
    assert outlet.verify(received)["sequence"] == 1


def test_changed_result_is_rejected(commission, outlet):
    forged = copy.deepcopy(commission.publish(1, RESULTS_V1))
    forged["payload"]["results"][0]["winner"] = "SOMEONE ELSE"
    with pytest.raises(Rejected, match="SIGNATURE INVALID"):
        outlet.verify(forged)


def test_payload_signed_by_another_key_is_rejected(outlet):
    impostor = ResultsPublisher().publish(1, RESULTS_V1)
    with pytest.raises(Rejected, match="different key"):
        outlet.verify(impostor)


#Criterion 2: a payload with a lower sequence number is rejected by the sample verifier
def test_lower_sequence_number_is_rejected(commission, outlet):
    v1 = commission.publish(1, RESULTS_V1)
    v2 = commission.publish(1, RESULTS_V2)
    outlet.verify(v2)
    with pytest.raises(Rejected, match="REPLAY"):
        outlet.verify(v1)  # an attacker replays the old, superseded result


def test_newer_sequence_is_accepted_in_order(commission, outlet):
    outlet.verify(commission.publish(1, RESULTS_V1))
    assert outlet.verify(commission.publish(1, RESULTS_V2))["sequence"] == 2


def test_sequence_number_cannot_be_edited(commission, outlet):
    bumped = copy.deepcopy(commission.publish(1, RESULTS_V1))
    bumped["payload"]["sequence"] = 99  # it is inside the signed payload
    with pytest.raises(Rejected, match="SIGNATURE INVALID"):
        outlet.verify(bumped)


def test_same_sequence_with_different_content_is_rejected(commission, outlet):
    outlet.verify(commission.publish(1, RESULTS_V1))
    second_publisher = ResultsPublisher(commission._private_key)  # e.g. a compromised server
    conflicting = second_publisher.publish(1, RESULTS_V2)  # also sequence 1
    with pytest.raises(Rejected, match="CONFLICT"):
        outlet.verify(conflicting)


def test_replay_protection_survives_a_restart(commission, tmp_path):
    state = tmp_path / "state.json"
    v1 = commission.publish(1, RESULTS_V1)
    v2 = commission.publish(1, RESULTS_V2)
    SampleVerifier(commission.public_key_pem, str(state)).verify(v2)

    restarted = SampleVerifier(commission.public_key_pem, str(state))
    with pytest.raises(Rejected, match="REPLAY"):
        restarted.verify(v1)


def test_elections_are_tracked_separately(commission, outlet):
    outlet.verify(commission.publish(1, RESULTS_V1))
    outlet.verify(commission.publish(1, RESULTS_V2))
    assert outlet.verify(commission.publish(2, RESULTS_V1))["sequence"] == 1


def test_future_timestamp_is_rejected(commission, outlet):
    publication = commission.publish(1, RESULTS_V1)
    an_hour_ago = dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=1)
    with pytest.raises(Rejected, match="future"):
        outlet.verify(publication, now=an_hour_ago)