

import copy
import json
import secrets

from sec16.tally import approved_recount, publish_house, publish_senate, recompute


def receipts_for(n):
    return [secrets.token_hex(16) for _ in range(n)]


#House: the classic IRV worked example (100 voters) 
house_candidates = {1: "MEMPHIS", 2: "NASHVILLE", 3: "CHATTANOOGA", 4: "KNOXVILLE"}
house_ballots = (
    [[1, 2, 3, 4]] * 42 + [[2, 3, 4, 1]] * 26 + [[3, 4, 2, 1]] * 15 + [[4, 3, 2, 1]] * 17
)
house = publish_house("TENN", house_candidates, house_ballots, receipts_for(100))

#Senate: the classic STV worked example (20 voters, 3 seats)
senate_candidates = {1: "ORANGE", 2: "PEAR", 3: "CHOCOLATE", 4: "STRAWBERRY", 5: "SWEETS"}
senate_ballots = [[1]] * 4 + [[2, 1]] * 2 + [[3, 4]] * 8 + [[3, 5]] * 4 + [[4]] + [[5]]
senate = publish_senate("VIC", senate_candidates, senate_ballots, 3, receipts_for(20))

print("1. Results")
print(f"   House winner: {house_candidates[house['winner']]}  (TCP {house['tcp']})")
names = [senate_candidates[c] for c in senate["elected"]]
print(f"   Senate elected (quota {senate['quota']}): {names}")

with open("published_results.json", "w", encoding="utf-8") as f:
    json.dump([house, senate], f, indent=2)
print("\n2. Published everything to published_results.json")

print("\n3. Independent recount of the published data:")
for result in (house, senate):
    problems = recompute(result)
    print(f"   {result['contest']:6}: {'VERIFIED' if not problems else problems}")

print("\n4. Court orders CHOCOLATE excluded - approved recount:")
recount = approved_recount(senate, 3, "HCA 3/2026", "VIC Commissioner's delegate")
print(f"   New senators elected: {[senate_candidates[c] for c in recount['elected']]}")
print(f"   Recount verifies: {'VERIFIED' if not recompute(recount) else 'FAILED'}")

print("\n5. Someone edits the published Senate result:")
forged = copy.deepcopy(senate)
forged["elected"] = [4, 1, 5]
for problem in recompute(forged):
    print(f"   CAUGHT: {problem}")