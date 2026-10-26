from __future__ import annotations

import random
from dataclasses import dataclass, field
from fractions import Fraction

from .count_house import break_tie

CONTINUING, ELECTED, EXCLUDED = "continuing", "elected", "excluded"


@dataclass
class SenateResult:
    vacancies: int
    formal: int
    quota: int
    elected: list[int] = field(default_factory=list)  # in the order they were elected
    excluded_order: list[int] = field(default_factory=list)
    manually_excluded: list[int] = field(default_factory=list)  # Court-ordered exclusions
    rounds: list[dict] = field(default_factory=list)  # the distribution of preferences

    def to_dict(self) -> dict:
        return {
            "vacancies": self.vacancies,
            "formal": self.formal,
            "quota": self.quota,
            "elected": self.elected,
            "excluded_order": self.excluded_order,
            "manually_excluded": self.manually_excluded,
            "rounds": self.rounds,
        }


def transfer_value(surplus: Fraction, total: Fraction) -> Fraction:
    """How much each of an elected candidate's ballots is worth when passed on."""
    return surplus / total


def _show(value: Fraction) -> float:
    """Fractions are exact; round only when displaying them."""
    return round(float(value), 4)


class SenateCount:
    """Keeps track of every ballot during one Senate count."""

    def __init__(self, candidate_ids, ballots, vacancies, excluded=(), seed=0):
        self.rng = random.Random(seed)
        self.vacancies = vacancies
        court_excluded = set(excluded)

        # Candidates in ballot-paper order, minus anyone the Court has excluded.
        self.candidates = [cid for cid in candidate_ids if cid not in court_excluded]
        self.status = {cid: CONTINUING for cid in self.candidates}

        # Each ballot keeps only preferences for candidates who are in the count.
        in_count = set(self.candidates)
        self.ballots = [[cid for cid in prefs if cid in in_count] for prefs in ballots]
        self.weight = [Fraction(1)] * len(self.ballots)  # value of each ballot
        self.position = [0] * len(self.ballots)  # which preference each ballot is on
        self.piles = {cid: [] for cid in self.candidates}  # ballot numbers each candidate holds
        self.exhausted = Fraction(0)  # value of ballots with no preferences left

        formal = len(ballots)
        self.result = SenateResult(
            vacancies=vacancies,
            formal=formal,
            quota=formal // (vacancies + 1) + 1,
            manually_excluded=[cid for cid in candidate_ids if cid in court_excluded],
        )
        self.history = []  # tallies at every count (for tie-breaking)
        self.surpluses_to_transfer = []  # elected candidates whose surplus is still waiting

    # ------------------------------------------------------------------ helpers
    @property
    def quota(self) -> int:
        return self.result.quota

    def continuing(self) -> list[int]:
        return [cid for cid in self.candidates if self.status[cid] == CONTINUING]

    def votes(self, cid: int) -> Fraction:
        return sum((self.weight[b] for b in self.piles[cid]), Fraction(0))

    def next_preference(self, ballot: int):
        """Move a ballot past anyone no longer continuing. Returns that candidate or None."""
        prefs = self.ballots[ballot]
        pos = self.position[ballot]
        while pos < len(prefs) and self.status[prefs[pos]] != CONTINUING:
            pos += 1
        self.position[ballot] = pos
        return prefs[pos] if pos < len(prefs) else None

    def give_ballots(self, ballot_numbers, multiply_weight_by: Fraction) -> None:
        """Pass ballots on to their next continuing preference."""
        for b in ballot_numbers:
            self.weight[b] *= multiply_weight_by
            receiver = self.next_preference(b)
            if receiver is None:
                self.exhausted += self.weight[b]
            else:
                self.piles[receiver].append(b)

    def record(self, action: str, **details) -> None:
        """Save a snapshot of the tallies, for the published distribution of preferences."""
        tallies = {cid: self.votes(cid) for cid in self.continuing()}
        self.history.append(tallies)
        self.result.rounds.append(
            {
                "count": len(self.result.rounds) + 1,
                "action": action,
                "tallies": {str(cid): _show(v) for cid, v in tallies.items()},
                "exhausted": _show(self.exhausted),
                **details,
            }
        )

    def elect(self, cid: int) -> None:
        self.status[cid] = ELECTED
        self.result.elected.append(cid)
        self.surpluses_to_transfer.append(cid)

    def note_elected(self, cids: list[int]) -> None:
        if cids:
            self.result.rounds[-1].setdefault("elected", []).extend(cids)

    def seats_left(self) -> int:
        return self.vacancies - len(self.result.elected)

    # ------------------------------------------------------------------ the count
    def run(self) -> SenateResult:
        # Step 1: first preferences.
        self.give_ballots(range(len(self.ballots)), Fraction(1))
        self.record("first preferences")
        if not self.candidates:
            return self.result

        while self.seats_left() > 0:
            votes = {cid: self.votes(cid) for cid in self.continuing()}

            # Step 2: elect everyone who has reached the quota (highest first).
            reached_quota = sorted(
                (cid for cid in votes if votes[cid] >= self.quota),
                key=lambda cid: (-votes[cid], self.candidates.index(cid)),
            )
            newly_elected = []
            for cid in reached_quota:
                if self.seats_left() > 0:
                    self.elect(cid)
                    newly_elected.append(cid)
            self.note_elected(newly_elected)
            if self.seats_left() == 0:
                break

            # Step 5: as many candidates left as seats left -> elect them all.
            remaining = self.continuing()
            if len(remaining) <= self.seats_left():
                remaining.sort(key=lambda cid: (-votes.get(cid, 0), self.candidates.index(cid)))
                for cid in remaining:
                    self.elect(cid)
                self.note_elected(remaining)
                self.result.rounds[-1]["note"] = "remaining candidates equal remaining vacancies"
                break

            # Step 3: transfer the next waiting surplus.
            if self.surpluses_to_transfer:
                self.transfer_surplus(self.surpluses_to_transfer.pop(0))
                continue

            # Step 4: no surplus waiting -> exclude the lowest candidate.
            self.exclude_lowest(votes)

        return self.result

    def transfer_surplus(self, cid: int) -> None:
        total = self.votes(cid)
        surplus = total - self.quota
        if surplus <= 0:
            return  # exactly a quota: nothing to pass on
        value = transfer_value(surplus, total)
        ballots, self.piles[cid] = self.piles[cid], []
        self.give_ballots(ballots, value)
        self.record(
            "surplus transfer", candidate=cid, surplus=_show(surplus), transfer_value=_show(value)
        )

    def exclude_lowest(self, votes: dict) -> None:
        fewest = min(votes.values())
        lowest = [cid for cid in self.continuing() if votes[cid] == fewest]
        if len(lowest) == 1:
            out, rule = lowest[0], "lowest votes"
        else:
            out, rule = break_tie(lowest, self.history, self.rng)

        self.status[out] = EXCLUDED
        self.result.excluded_order.append(out)
        ballots, self.piles[out] = self.piles[out], []
        self.give_ballots(ballots, Fraction(1))  # passed on at their current value
        self.record("exclusion", candidate=out, rule=rule)


def count(candidate_ids, ballots, vacancies, excluded=(), seed=0) -> SenateResult:
    """Run a Senate count. `ballots` are formal preference orders (lists of candidate ids)."""
    return SenateCount(candidate_ids, ballots, vacancies, excluded, seed).run()