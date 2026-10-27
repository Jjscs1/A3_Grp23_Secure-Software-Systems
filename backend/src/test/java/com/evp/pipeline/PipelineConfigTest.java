package com.evp.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * SEC-20 acceptance criteria, checked against the pipeline CONFIGURATION.
 *
 * The pipeline itself runs on GitHub. These tests run inside it (./mvnw test), so a pull
 * request that deletes a scan or weakens branch protection fails its own pipeline and
 * cannot be merged.
 */
class PipelineConfigTest {

    /** ./mvnw test runs in backend/, so the repository root is one level up. */
    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();

    private static Map<String, Object> workflow;
    private static JsonNode protection;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws IOException {
        workflow = new Yaml().load(Files.readString(REPO.resolve(".github/workflows/security.yml")));
        protection = new ObjectMapper().readTree(REPO.resolve(".github/branch-protection.json").toFile());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> jobs() {
        return (Map<String, Map<String, Object>>) workflow.get("jobs");
    }

    /** Everything a job runs or uses, as one string, so tests can search it. */
    @SuppressWarnings("unchecked")
    private static String commands(String job) {
        StringBuilder all = new StringBuilder();
        Object container = jobs().get(job).get("container");
        if (container != null) {
            all.append(container).append('\n');
        }
        for (Map<String, Object> step : (List<Map<String, Object>>) jobs().get(job).get("steps")) {
            all.append(step.getOrDefault("uses", "")).append('\n')
                    .append(step.getOrDefault("run", "")).append('\n')
                    .append(step.getOrDefault("with", "")).append('\n');
        }
        return all.toString();
    }

    /** Status check names as GitHub shows them (matrix jobs expand to one check per value). */
    @SuppressWarnings("unchecked")
    private static List<String> checkNames() {
        List<String> names = new ArrayList<>();
        jobs().forEach((id, job) -> {
            String name = (String) job.get("name");
            Map<String, Object> strategy = (Map<String, Object>) job.get("strategy");
            if (strategy == null) {
                names.add(name);
            } else {
                List<String> languages = (List<String>) ((Map<String, Object>) strategy.get("matrix")).get("language");
                languages.forEach(l -> names.add(name.replace("${{ matrix.language }}", l)));
            }
        });
        return names;
    }

    // ===== Criterion 1: a PR can't merge without review and passing security checks

    @Test
    @DisplayName("SEC-20: the pipeline runs on every pull request to main")
    @SuppressWarnings("unchecked")
    void runsOnPullRequests() {
        // SnakeYAML reads the bare key `on` as the boolean true (YAML 1.1).
        Map<String, Object> triggers = (Map<String, Object>) (workflow.containsKey("on")
                ? workflow.get("on") : workflow.get(Boolean.TRUE));
        assertThat(((Map<String, Object>) triggers.get("pull_request")).get("branches").toString()).contains("main");
    }

    @Test
    @DisplayName("SEC-20: every pipeline job is a required status check")
    void everyJobIsRequired() {
        List<String> required = new ArrayList<>();
        protection.at("/required_status_checks/contexts").forEach(c -> required.add(c.asText()));
        assertThat(required).containsExactlyInAnyOrderElementsOf(checkNames());
        assertThat(protection.at("/required_status_checks/strict").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("SEC-20: at least one code-owner approval is required, and new commits need a new one")
    void reviewRequired() {
        JsonNode reviews = protection.get("required_pull_request_reviews");
        assertThat(reviews.get("required_approving_review_count").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(reviews.get("require_code_owner_reviews").asBoolean()).isTrue();
        assertThat(reviews.get("dismiss_stale_reviews").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("SEC-20: admins cannot bypass the rules, and history cannot be rewritten")
    void noBypass() {
        assertThat(protection.get("enforce_admins").asBoolean()).isTrue();
        assertThat(protection.get("allow_force_pushes").asBoolean()).isFalse();
        assertThat(protection.get("allow_deletions").asBoolean()).isFalse();
    }

    // ===== Criterion 2: pipeline runs tests, Semgrep/CodeQL, a dependency scan and gitleaks

    @Test
    @DisplayName("SEC-20: the pipeline runs the backend and frontend tests")
    void runsTests() {
        assertThat(commands("backend-tests")).contains("./mvnw -B test");
        assertThat(commands("frontend-tests")).contains("npm test").contains("npm run lint");
    }

    @Test
    @DisplayName("SEC-20: the pipeline runs Semgrep (failing on findings) and CodeQL for Java and TypeScript")
    void runsSast() {
        assertThat(commands("semgrep")).contains("semgrep scan").contains("--error");
        assertThat(commands("codeql")).contains("github/codeql-action/analyze");
        assertThat(checkNames()).contains("SAST - CodeQL (java-kotlin)", "SAST - CodeQL (javascript-typescript)");
    }

    @Test
    @DisplayName("SEC-20: the pipeline runs a dependency scan")
    void runsDependencyScan() {
        assertThat(commands("dependency-scan")).contains("osv_scan.py");
    }

    @Test
    @DisplayName("SEC-20: the pipeline runs gitleaks over the full git history")
    void runsGitleaks() {
        String gitleaks = commands("secret-scan");
        assertThat(gitleaks).contains("gitleaks git").contains("--exit-code 1").contains("fetch-depth=0");
    }

    // ===== Criterion 3: the pipeline produces an SBOM

    @Test
    @DisplayName("SEC-20: the pipeline produces, checks and keeps an SBOM for backend and frontend")
    void producesSbom() {
        assertThat(commands("sbom")).contains("cyclonedx-maven-plugin").contains("cyclonedx-npm")
                .contains("check_sbom.py").contains("actions/upload-artifact");
    }

    // ===== The pipeline must not be the weak point itself

    @Test
    @DisplayName("SEC-20: the pipeline token is read-only by default and checkout keeps no credentials")
    @SuppressWarnings("unchecked")
    void leastPrivilege() {
        assertThat(workflow.get("permissions")).isEqualTo(Map.of("contents", "read"));
        jobs().keySet().forEach(job -> assertThat(commands(job)).contains("persist-credentials=false"));
    }

    @Test
    @DisplayName("SEC-20: every accepted dependency risk has a reason and an expiry within a year")
    void allowlistEntriesExpire() throws IOException {
        for (String line : Files.readAllLines(REPO.resolve(".github/dependency-allowlist.txt"))) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+", 3);
            assertThat(parts).as(line).hasSize(3);
            assertThat(parts[1]).startsWith("review-by=");
            LocalDate reviewBy = LocalDate.parse(parts[1].substring("review-by=".length()));
            assertThat(reviewBy).isBefore(LocalDate.now().plusYears(1));
        }
    }
}