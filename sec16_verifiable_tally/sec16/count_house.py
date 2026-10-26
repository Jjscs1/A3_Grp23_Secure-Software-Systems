from __future__ import annotations

import random
from dataclasses import asdict, dataclass, field


@dataclass
class HouseResult:
    winner: int | None
    formal: int
    rounds: list[dict] = field(default_factory=list)  # one entry per count
    tcp: dict[int, int] = field(default_factory=dict)  # final two-candidate totals
    majority_round: int | None = None  # first count where someone had > 50%

    def to_dict(self) -> dict:
        data = asdict(self)
        data["tcp"] = {str(cid): votes for cid, votes in self.tcp.items()}  # JSON keys are strings
        return data


def break_tie(tied: list[int], history: list[dict], rng: random.Random) -> tuple[int, str]:
    """Choose which of several tied candidates to exclude.

    Walk backwards through earlier counts; whoever had the fewest votes at the
    most recent count where they differed is excluded. Otherwise use the lot.
    """
    still_tied = list(tied)
    earlier_counts = reversed(history[:-1])  # every count before this one, newest first

    for tallies in earlier_counts:
        lowest = min(tallies.get(cid, 0) for cid in still_tied)
        lowest_then = [cid for cid in still_tied if tallies.get(cid, 0) == lowest]
        if len(lowest_then) < len(still_tied):
            still_tied = lowest_then
            if len(still_tied) == 1:
                return still_tied[0], "earlier count"

    return rng.choice(sorted(still_tied)), "lot"


def tally_first_preferences(ballots: list[list[int]], continuing: list[int]) -> tuple[dict, int]:
    """Give each ballot to its highest-ranked continuing candidate.

    Returns (votes per candidate, number of exhausted ballots).
    """
    still_in = set(continuing)
    votes = {cid: 0 for cid in continuing}
    exhausted = 0

    for preferences in ballots:
        for cid in preferences:
            if cid in still_in:
                votes[cid] += 1
                break
        else:  # the loop found nobody still in the count
            exhausted += 1

    return votes, exhausted


def count(candidate_ids: list[int], ballots: list[list[int]], seed: int = 0) -> HouseResult:
    """Run the count. `ballots` are formal preference orders, e.g. [[3, 1, 2], [1, 2, 3]]."""
    if not candidate_ids:
        raise ValueError("no candidates")

    rng = random.Random(seed)
    continuing = list(candidate_ids)
    result = HouseResult(winner=None, formal=len(ballots))

    # Only one candidate: elected unopposed.
    if len(continuing) == 1:
        only = continuing[0]
        result.winner = only
        result.tcp = {only: len(ballots)}
        result.rounds.append(
            {
                "round": 1,
                "tallies": {str(only): len(ballots)},
                "exhausted": 0,
                "note": "elected unopposed",
            }
        )
        return result

    history = []  # the tallies from every count, used for tie-breaking
    while True:
        votes, exhausted = tally_first_preferences(ballots, continuing)
        history.append(votes)
        round_info = {
            "round": len(history),
            "tallies": {str(cid): v for cid, v in votes.items()},
            "exhausted": exhausted,
        }

        # Remember the first count where someone had an absolute majority.
        live_ballots = len(ballots) - exhausted
        if (
            result.majority_round is None
            and live_ballots
            and max(votes.values()) * 2 > live_ballots
        ):
            result.majority_round = len(history)

        # Two candidates left: the higher one wins.
        if len(continuing) == 2:
            first, second = continuing
            if votes[first] == votes[second]:
                winner, rule = rng.choice([first, second]), "lot (tied two-candidate-preferred)"
            elif votes[first] > votes[second]:
                winner, rule = first, "majority"
            else:
                winner, rule = second, "majority"

            round_info["elected"] = winner
            round_info["rule"] = rule
            result.rounds.append(round_info)
            result.winner = winner
            result.tcp = dict(votes)
            return result

        # Otherwise exclude the lowest candidate.
        fewest = min(votes.values())
        lowest = [cid for cid in continuing if votes[cid] == fewest]
        if len(lowest) == 1:
            excluded, rule = lowest[0], "lowest votes"
        else:
            excluded, rule = break_tie(lowest, history, rng)

        round_info["excluded"] = excluded
        round_info["rule"] = rule
        result.rounds.append(round_info)
        continuing.remove(excluded)