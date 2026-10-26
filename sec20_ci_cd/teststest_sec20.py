import json
from pathlib import Path

import pytest
import yaml

from check_sbom import problems_in
from collect_requirements import collect
from run_all_tests import find_modules

REPO_ROOT = Path(__file__).resolve().parents[2]
GITHUB = REPO_ROOT / ".github"


@pytest.fixture(scope="module")
def workflow():
    return yaml.safe_load((GITHUB / "workflows" / "security.yml").read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def protection():
    return json.loads((GITHUB / "branch-protection.json").read_text(encoding="utf-8"))


def job_commands(job: dict) -> str:
    """Everything a job runs or uses, as one string (easy to search)."""
    parts = [job.get("container", {}).get("image", "")] if "container" in job else []
    for step in job["steps"]:
        parts += [step.get("uses", ""), step.get("run", ""), json.dumps(step.get("with", {}))]
    return "\n".join(parts)


def find_job(workflow: dict, keyword: str) -> dict:
    matches = [job for job in workflow["jobs"].values() if keyword in job_commands(job)]
    assert matches, f"no pipeline job uses {keyword!r}"
    return matches[0]


#Criterion 1: a PR can't merge without review and passing security checks
def test_pipeline_runs_on_every_pull_request_to_main(workflow):
    triggers = workflow.get("on", workflow.get(True))  # YAML reads a bare `on` as True
    assert "main" in triggers["pull_request"]["branches"]
    assert "main" in triggers["push"]["branches"]


def test_every_pipeline_job_is_a_required_check(workflow, protection):
    job_names = {job["name"] for job in workflow["jobs"].values()}
    assert set(protection["required_status_checks"]["contexts"]) == job_names


def test_branch_must_be_up_to_date_before_merging(protection):
    assert protection["required_status_checks"]["strict"] is True


def test_at_least_one_approving_review_is_required(protection):
    reviews = protection["required_pull_request_reviews"]
    assert reviews["required_approving_review_count"] >= 1
    assert reviews["require_code_owner_reviews"] is True
    assert reviews["dismiss_stale_reviews"] is True  # new commits need a new approval


def test_admins_cannot_bypass_and_history_cannot_be_rewritten(protection):
    assert protection["enforce_admins"] is True
    assert protection["allow_force_pushes"] is False
    assert protection["allow_deletions"] is False


def test_every_codeowners_rule_has_at_least_two_reviewers():
    for line in (GITHUB / "CODEOWNERS").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            owners = [word for word in line.split() if word.startswith("@")]
            assert len(owners) >= 2, f"only one owner for: {line}"


#Criterion 2: pipeline runs tests, Semgrep/CodeQL, a dependency scan and gitleaks
def test_pipeline_runs_the_tests_of_every_module(workflow):
    assert "run_all_tests.py" in job_commands(workflow["jobs"]["tests"])


def test_pipeline_runs_semgrep_and_fails_on_findings(workflow):
    commands = job_commands(find_job(workflow, "semgrep scan"))
    assert "--error" in commands


def test_pipeline_runs_codeql_with_permission_to_report(workflow):
    job = find_job(workflow, "github/codeql-action/analyze")
    assert job["permissions"]["security-events"] == "write"


def test_pipeline_runs_a_dependency_scan(workflow):
    assert "--strict" in job_commands(find_job(workflow, "pip-audit -r"))


def test_pipeline_runs_gitleaks_over_the_full_history(workflow):
    job = find_job(workflow, "gitleaks git")
    assert "--exit-code 1" in job_commands(job)
    checkout = next(s for s in job["steps"] if s.get("uses", "").startswith("actions/checkout"))
    assert checkout["with"]["fetch-depth"] == 0


def test_test_runner_finds_every_module(tmp_path):
    for name in ("sec15_a", "sec16_b"):
        (tmp_path / name / "tests").mkdir(parents=True)
        (tmp_path / name / "requirements.txt").write_text("pytest\n")
    (tmp_path / "docs").mkdir()  # not a module: no tests or requirements
    assert [m.name for m in find_modules(tmp_path)] == ["sec15_a", "sec16_b"]


def test_this_repo_modules_are_all_found():
    assert "sec20_ci_cd" in [m.name for m in find_modules()]


def test_requirements_are_merged_without_duplicates(tmp_path):
    (tmp_path / "a").mkdir()
    (tmp_path / "b").mkdir()
    (tmp_path / "a" / "requirements.txt").write_text("cryptography>=42.0\npytest>=8.0\n")
    (tmp_path / "b" / "requirements.txt").write_text("# comment\npytest>=8.0\n\nFlask>=3.0\n")
    assert collect(tmp_path) == ["cryptography>=42.0", "Flask>=3.0", "pytest>=8.0"]


#Criterion 3: the pipeline produces an SBOM
def test_pipeline_generates_checks_and_uploads_an_sbom(workflow):
    commands = job_commands(find_job(workflow, "cyclonedx-py"))
    assert "check_sbom.py" in commands
    assert "actions/upload-artifact" in commands


def test_valid_sbom_passes_the_check():
    sbom = {"bomFormat": "CycloneDX", "components": [{"name": "flask", "version": "3.1.0"}]}
    assert problems_in(sbom) == []


def test_empty_or_unversioned_sbom_fails_the_check():
    assert problems_in({"bomFormat": "CycloneDX", "components": []})
    assert problems_in({"bomFormat": "CycloneDX", "components": [{"name": "flask"}]})
    assert problems_in({"bomFormat": "SPDX", "components": [{"name": "x", "version": "1"}]})


#Pipeline hardening (the pipeline must not be the weak point itself)
def test_token_is_read_only_by_default(workflow):
    assert workflow["permissions"] == {"contents": "read"}


def test_checkout_never_leaves_credentials_on_disk(workflow):
    for job in workflow["jobs"].values():
        for step in job["steps"]:
            if step.get("uses", "").startswith("actions/checkout"):
                assert step["with"]["persist-credentials"] is False