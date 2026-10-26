import copy
import json

import pytest

from sec16 import count_house, count_senate
from sec16.merkle import merkle_root
from sec16.tally import RecountError, approved_recount, publish_house, publish_senate, recompute

# Classic IRV worked example: the Tennessee capital election (100 voters).
MEMPHIS, NASHVILLE, CHATTANOOGA, KNOXVILLE = 1, 2, 3, 4
TENNESSEE = (
    [[MEMPHIS, NASHVILLE, CHATTANOOGA, KNOXVILLE]] * 42
    + [[NASHVILLE, CHATTANOOGA, KNOXVILLE, MEMPHIS]] * 26
    + [[CHATTANOOGA, KNOXVILLE, NASHVILLE, MEMPHIS]] * 15
    + [[KNOXVILLE, CHATTANOOGA, NASHVILLE, MEMPHIS]] * 17
)

# Classic STV worked example: 20 voters, 3 seats, Droop quota = 20 // 4 + 1 = 6.
ORANGE, PEAR, CHOC, STRAW, SWEETS = 1, 2, 3, 4, 5
DESSERTS = (
    [[ORANGE]] * 4
    + [[PEAR, ORANGE]] * 2
    + [[CHOC, STRAW]] * 8
    + [[CHOC, SWEETS]] * 4
    + [[STRAW]]
    + [[SWEETS]]
)
DESSERT_NAMES = {
    ORANGE: "Orange",
    PEAR: "Pear",
    CHOC: "Chocolate",
    STRAW: "Strawberry",
    SWEETS: "Sweets",
}


def receipts(n):
    return [f"{i:032x}" for i in range(n)]


@pytest.fixture
def published_senate():
    return publish_senate("VIC", DESSERT_NAMES, DESSERTS, 3, receipts(20))


# ======= Criterion 1: IRV and STV unit tests reproduce known worked examples ==
def test_irv_tennessee_example():
    result = count_house.count([MEMPHIS, NASHVILLE, CHATTANOOGA, KNOXVILLE], TENNESSEE)
    # Memphis leads on first preferences (42) but has no majority.
    assert result.rounds[0]["tallies"] == {"1": 42, "2": 26, "3": 15, "4": 17}
    # Chattanooga then Nashville are excluded; Knoxville wins 58-42.
    assert [r.get("excluded") for r in result.rounds[:2]] == [CHATTANOOGA, NASHVILLE]
    assert result.winner == KNOXVILLE
    assert result.tcp == {MEMPHIS: 42, KNOXVILLE: 58}


def test_stv_three_seat_example():
    result = count_senate.count([ORANGE, PEAR, CHOC, STRAW, SWEETS], DESSERTS, vacancies=3)
    assert result.quota == 6
    assert result.elected == [CHOC, ORANGE, STRAW]
    # Chocolate's 12 votes are 6 over quota, so each ballot moves on at value 6/12 = 0.5.
    surplus = next(r for r in result.rounds if r["action"] == "surplus transfer")
    assert surplus["transfer_value"] == 0.5
    assert surplus["tallies"][str(STRAW)] == 5.0 and surplus["tallies"][str(SWEETS)] == 3.0


def test_irv_exclusion_tie_uses_earlier_count():
    # C and D tie on 2 votes at count 2; C had fewer at count 1, so C goes first.
    ballots = [[1]] * 5 + [[2]] * 3 + [[3]] * 2 + [[4, 3]]
    result = count_house.count([1, 2, 3, 4], ballots)
    assert result.rounds[1]["excluded"] == 3 and result.rounds[1]["rule"] == "earlier count"


# ========= Criterion 2: a recount with an approved exclusion gives the expected new result
def test_recount_with_approved_exclusion(published_senate):
    recount = approved_recount(published_senate, CHOC, "HCA 3/2026", "VIC delegate")
    # Without Chocolate, its 12 ballots go straight to Strawberry (9) and Sweets (5).
    assert recount["elected"] == [STRAW, ORANGE, SWEETS]
    assert recount["manually_excluded"] == [CHOC]
    assert recount["recount_orders"][0]["court_reference"] == "HCA 3/2026"


def test_exclusion_without_approval_is_refused(published_senate):
    with pytest.raises(RecountError):
        approved_recount(published_senate, CHOC, court_reference="", approved_by="VIC delegate")
    with pytest.raises(RecountError):
        approved_recount(published_senate, 99, "HCA 3/2026", "VIC delegate")


# ================ Criterion 3: published data recomputes to the same result
def test_published_house_result_recomputes():
    published = publish_house("TENN", {1: "M", 2: "N", 3: "C", 4: "K"}, TENNESSEE, receipts(100))
    assert recompute(published) == []


def test_published_senate_result_and_recount_recompute(published_senate):
    assert recompute(published_senate) == []
    assert recompute(approved_recount(published_senate, CHOC, "HCA 3/2026", "VIC delegate")) == []


def test_recompute_works_after_saving_to_json(published_senate):
    """What the public actually downloads is JSON, so verify a JSON round trip."""
    downloaded = json.loads(json.dumps(published_senate))
    assert recompute(downloaded) == []


def test_tampered_winner_is_detected(published_senate):
    forged = copy.deepcopy(published_senate)
    forged["elected"] = [STRAW, ORANGE, SWEETS]
    assert any("elected differ" in p for p in recompute(forged))


def test_removed_ballot_is_detected(published_senate):
    forged = copy.deepcopy(published_senate)
    forged["ballots"].pop()
    assert recompute(forged)


def test_removed_receipt_is_detected(published_senate):
    forged = copy.deepcopy(published_senate)
    forged["ballot_box"]["receipts"].pop()
    assert any("Merkle root" in p for p in recompute(forged))


def test_secret_exclusion_without_court_order_is_detected(published_senate):
    forged = copy.deepcopy(published_senate)
    forged["count_inputs"]["excluded"] = [CHOC]
    assert any("court order" in p for p in recompute(forged))


def test_published_ballots_are_sorted_and_root_ignores_order():
    published = publish_house("TENN", {1: "M", 2: "N", 3: "C", 4: "K"}, TENNESSEE, receipts(100))
    assert published["ballots"] == sorted(published["ballots"])
    assert merkle_root(receipts(5)) == merkle_root(list(reversed(receipts(5))))