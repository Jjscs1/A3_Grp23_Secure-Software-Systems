"""SEC-15 walkthrough - run with:  python demo.py"""

from sec15 import shamir
from sec15.election import Election, PollStillOpen
from sec15.election_keys import key_ceremony

print("1. Key ceremony: election key locked, unlock key split 3-of-5")
keys, shares = key_ceremony()
for number, share in enumerate(shares, start=1):
    print(f"   Trustee {number}: {share[:28]}...")

election = Election(keys)
for choice in ["Alice", "Bob", "Alice", "Carol"]:
    election.cast({"choice": choice})
print(f"\n2. {len(election.ballot_box)} encrypted ballots cast")

print("\n3. Try to decrypt while the poll is OPEN, with all 5 shares:")
try:
    election.decrypt_all(shares)
except PollStillOpen as error:
    print(f"   BLOCKED (423 Locked): {error}")

election.close_poll()
print("\n4. Poll closed. Try with only 2 shares:")
try:
    election.decrypt_all(shares[:2])
except shamir.ShareError as error:
    print(f"   REJECTED: {error}")

print("\n5. Trustees 1, 3 and 5 combine their shares:")
ballots = election.decrypt_all([shares[0], shares[2], shares[4]])
print(f"   SUCCESS - decrypted ballots: {[b['choice'] for b in ballots]}")