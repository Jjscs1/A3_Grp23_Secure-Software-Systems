package com.evp.ballotbox;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only. Contains only hashes and signatures, never ballots. Permit GET /api/ballotbox/** in SecurityConfig. */
@RestController
@RequestMapping("/api/ballotbox")
public class BallotBoxController {
    private final BallotBox box;
    private final BallotBoxMonitor monitor;

    public BallotBoxController(BallotBox box, BallotBoxMonitor monitor) {
        this.box = box;
        this.monitor = monitor;
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/key")
    public SignerInfo key() {
        return box.signerInfo();
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/checkpoint")
    public ResponseEntity<Checkpoint> checkpoint() {
        return box.latest().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/proof")
    public ResponseEntity<BallotBox.InclusionProof> proof(@RequestParam("leaf") String leaf) {
        if (!leaf.matches("[0-9a-f]{64}")) {
            return ResponseEntity.badRequest().build();
        }
        return box.prove(leaf).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/consistency")
    public ResponseEntity<BallotBox.ConsistencyProof> consistency(@RequestParam("first") long first, @RequestParam("second") long second) {
        return box.consistency(first, second).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/status")
    public Map<String, Object> status() {
        BallotBox.Status s = monitor.status();
        return Map.of("ok", s.ok(), "size", s.size(), "checkpoints", s.checkpoints());
    }
}
