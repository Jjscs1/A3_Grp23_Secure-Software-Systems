import copy
from pathlib import Path

from sec18.publisher import ResultsPublisher
from sec18.verifier import Rejected, SampleVerifier
# Each run makes a brand-new election key, so forget what the command-line
# verifier accepted last time (otherwise it would report a CONFLICT).
Path("verifier_state.json").unlink(missing_ok=True)

#the commission
commission = ResultsPublisher()
commission.save_public_key("election_public.pem")

v1 = commission.publish(1, [{"contest": "house", "region": "MELB", "winner": "NGUYEN, Ava"}])
v2 = commission.publish(1, [{"contest": "house", "region": "MELB", "winner": "SINGH, Maya"}])
ResultsPublisher.save(v1, "results_v1.json")
ResultsPublisher.save(v2, "results_v2.json")
print("1. Commission published two versions (v2 corrects v1) and its public key")
print(f"   key_id = {commission.key_id}")

#a media outlet
outlet = SampleVerifier(commission.public_key_pem)


def check(label, publication):
    try:
        payload = outlet.verify(publication)
        print(f"   {label:38} ACCEPTED (sequence {payload['sequence']})")
    except Rejected as reason:
        print(f"   {label:38} REJECTED: {reason}")


print("\n2. The media outlet's sample verifier checks what it receives:")
check("v1 (genuine)", v1)
check("v2 (genuine correction)", v2)
check("v1 again (attacker replays old result)", v1)

forged = copy.deepcopy(v2)
forged["payload"]["results"][0]["winner"] = "SOMEONE ELSE"
check("v2 with the winner changed", forged)

bumped = copy.deepcopy(v1)
bumped["payload"]["sequence"] = 99
check("v1 with its sequence edited to 99", bumped)

impostor = ResultsPublisher().publish(1, [{"contest": "house", "winner": "FAKE"}])
check("payload signed by a different key", impostor)