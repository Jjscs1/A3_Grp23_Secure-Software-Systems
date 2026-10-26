from __future__ import annotations

import datetime as dt

from . import count_house, count_senate
from .merkle import merkle_root


class RecountError(ValueError):
    """Raised when a recount exclusion has not been properly approved."""


def _box(receipts: list[str]) -> dict:
    return {
        "ballot_count": len(receipts),
        "receipts": sorted(receipts),
        "receipts_merkle_root": merkle_root(receipts),
    }

# Counting and publishing

def publish_house(region, candidates, ballots, receipts, seed=0) -> dict:
    """Count a House division and return the full public result.

    candidates: {candidate_id: "NAME, Given"} in ballot-paper order
    ballots:    formal preference orders, e.g. [[2, 1, 3], [1, 3, 2], ...]
    """
    candidate_ids = list(candidates)
    outcome = count_house.count(candidate_ids, ballots, seed=seed)
    return {
        "contest": "house",
        "region": region,
        "method": "full preferential (instant run-off), distributed to two candidates",
        "candidates": {str(cid): name for cid, name in candidates.items()},
        "count_inputs": {"candidate_ids": candidate_ids, "excluded": [], "tiebreak_seed": seed},
        "ballots": sorted(ballots),
        "ballot_box": _box(receipts),
        **outcome.to_dict(),
    }


def publish_senate(
    region, candidates, ballots, vacancies, receipts, excluded=(), recount_orders=(), seed=0
) -> dict:
    """Count a Senate contest (STV) and return the full public result."""
    candidate_ids = list(candidates)
    outcome = count_senate.count(
        candidate_ids, ballots, vacancies, excluded=list(excluded), seed=seed
    )
    return {
        "contest": "senate",
        "region": region,
        "method": "single transferable vote, Droop quota, weighted surplus transfer",
        "candidates": {str(cid): name for cid, name in candidates.items()},
        "count_inputs": {
            "candidate_ids": candidate_ids,
            "vacancies": vacancies,
            "excluded": sorted(excluded),
            "tiebreak_seed": seed,
        },
        "recount_orders": list(recount_orders),
        "ballots": sorted(ballots),
        "ballot_box": _box(receipts),
        **outcome.to_dict(),
    }

# Court of Disputed Returns recount

def approved_recount(published: dict, exclude_id: int, court_reference: str, approved_by: str):
    """Re-run a Senate count with one candidate excluded.

    The exclusion must be APPROVED: it needs a court order reference and the
    name of the Commissioner's delegate who approved it. Both are published
    with the new result, so the public can see why the result changed.
    """
    if published["contest"] != "senate":
        raise RecountError("Recount exclusions apply to Senate contests only.")
    if not court_reference.strip() or not approved_by.strip():
        raise RecountError("An exclusion needs a court order reference and an approver.")
    if str(exclude_id) not in published["candidates"]:
        raise RecountError(f"Candidate {exclude_id} did not stand in this contest.")

    inputs = published["count_inputs"]
    order = {
        "candidate_id": exclude_id,
        "court_reference": court_reference.strip(),
        "approved_by": approved_by.strip(),
        "approved_at": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
    }
    return publish_senate(
        published["region"],
        {int(cid): name for cid, name in published["candidates"].items()},
        published["ballots"],
        inputs["vacancies"],
        published["ballot_box"]["receipts"],
        excluded=[*inputs["excluded"], exclude_id],
        recount_orders=[*published.get("recount_orders", []), order],
        seed=inputs["tiebreak_seed"],
    )

# Public verification

def recompute(published: dict) -> list[str]:
    """Re-run the count from the published data. Returns a list of problems (empty = OK)."""
    problems = []
    where = f"{published['contest']} {published['region']}"
    inputs = published["count_inputs"]
    box = published["ballot_box"]

    # 1. The receipt list must match its published fingerprint.
    if merkle_root(box["receipts"]) != box["receipts_merkle_root"]:
        problems.append(f"{where}: receipt list does not match the published Merkle root")
    if len(box["receipts"]) != box["ballot_count"]:
        problems.append(f"{where}: ballot count does not match the number of receipts")

    # 2. The number of published ballots must match the declared formal total.
    published_count = len(published["ballots"])
    if published_count != published["formal"]:
        problems.append(
            f"{where}: {published_count} ballots published, formal = {published['formal']}"
        )

    # 3. Excluded candidates must each have an approved court order.
    ordered = {o["candidate_id"] for o in published.get("recount_orders", [])}
    if set(inputs["excluded"]) - ordered:
        problems.append(f"{where}: a candidate was excluded without an approved court order")

    # 4. Re-run the count with the open-source code and compare the outcome.
    if published["contest"] == "house":
        again = count_house.count(
            inputs["candidate_ids"], published["ballots"], seed=inputs["tiebreak_seed"]
        ).to_dict()
        if again["winner"] != published["winner"] or again["tcp"] != published["tcp"]:
            problems.append(f"{where}: recomputed winner / two-candidate result differs")
    else:
        again = count_senate.count(
            inputs["candidate_ids"],
            published["ballots"],
            inputs["vacancies"],
            excluded=inputs["excluded"],
            seed=inputs["tiebreak_seed"],
        ).to_dict()
        if again["elected"] != published["elected"] or again["quota"] != published["quota"]:
            problems.append(f"{where}: recomputed senators elected differ")

    if again["rounds"] != published["rounds"]:
        problems.append(f"{where}: recomputed distribution of preferences differs")
    return problems