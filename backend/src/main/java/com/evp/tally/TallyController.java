package com.evp.tally;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TallyController {

    private final TallyService service;

    public TallyController(TallyService service) {
        this.service = service;
    }

    public record CandidateInput(@Min(1) int id, @NotBlank @Size(max = 120) String name) {
    }

    public record HouseRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9 _-]+$") String region,
            @NotEmpty @Size(max = 100) List<@Valid CandidateInput> candidates,
            @NotNull @Size(max = 1_000_000) List<@NotNull List<@NotNull Integer>> ballots,
            @NotNull List<@NotBlank @Size(max = 64) String> receipts,
            Long tiebreakSeed) {
    }

    public record SenateRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9 _-]+$") String region,
            @NotEmpty @Size(max = 200) List<@Valid CandidateInput> candidates,
            @NotNull @Size(max = 1_000_000) List<@NotNull List<@NotNull Integer>> ballots,
            @Min(1) @Max(12) int vacancies,
            @NotNull List<@NotBlank @Size(max = 64) String> receipts,
            Long tiebreakSeed) {
    }

    public record TallySummary(Long id, String contest, String region, Instant publishedAt, Long recountOf) {
        static TallySummary of(PublishedTally t) {
            return new TallySummary(t.getId(), t.getContest(), t.getRegion(), t.getPublishedAt(), t.getRecountOf());
        }
    }

    public record VerifyResponse(Long id, boolean verified, List<String> problems) {
    }

    @PostMapping("/tally/house")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ELECTION_ADMIN')")
    public TallySummary house(@Valid @RequestBody HouseRequest r, Principal admin) {
        return TallySummary.of(service.publishHouse(r.region(), candidates(r.candidates()), r.ballots(),
                r.receipts(), seed(r.tiebreakSeed()), admin.getName()));
    }

    @PostMapping("/tally/senate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ELECTION_ADMIN')")
    public TallySummary senate(@Valid @RequestBody SenateRequest r, Principal admin) {
        return TallySummary.of(service.publishSenate(r.region(), candidates(r.candidates()), r.ballots(),
                r.vacancies(), r.receipts(), seed(r.tiebreakSeed()), admin.getName()));
    }

    @PostMapping("/tally/{id}/recount")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ELECTION_ADMIN')")
    public TallySummary recount(@PathVariable long id, Principal admin) {
        return TallySummary.of(service.recount(id, admin.getName()));
    }

    @GetMapping("/public/tally")
    public List<TallySummary> list() {
        return service.all().stream().map(TallySummary::of).toList();
    }

    @GetMapping(value = "/public/tally/{id}", produces = "application/json")
    public String document(@PathVariable long id) {
        return service.get(id).getDocument();
    }

    @GetMapping("/public/tally/{id}/verify")
    public VerifyResponse verify(@PathVariable long id) {
        List<String> problems = service.verify(id);
        return new VerifyResponse(id, problems.isEmpty(), problems);
    }

    private static List<TallyDocument.Candidate> candidates(List<CandidateInput> in) {
        return in.stream().map(c -> new TallyDocument.Candidate(c.id(), c.name())).toList();
    }

    private static long seed(Long given) {
        return given == null ? 0L : given;
    }
}