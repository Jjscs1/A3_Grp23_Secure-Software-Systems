package com.evp.ballotbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 5 minutes: seal pending ballots, then re-verify the whole log. Counting asks {@link #requireIntact()}. */
@Component
public class BallotBoxMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(BallotBoxMonitor.class);

    private final BallotBox box;
    private volatile BallotBox.Status last = new BallotBox.Status(false, 0, 0, "not verified yet");

    @Autowired
    public BallotBoxMonitor(BallotBox box) {
        this.box = box;
    }

    @Scheduled(fixedDelayString = "${evp.ballotbox.interval-ms:300000}", initialDelay = 5000)
    public void run() {
        try {
            box.seal();
            last = box.verify();
        } catch (Exception e) {
            last = BallotBox.Status.bad(last.size(), last.checkpoints(), "monitor error");
        }
        if (!last.ok()) {
            LOG.error("BALLOT BOX INTEGRITY FAILURE: {}", last.problem());
        }
    }

    public BallotBox.Status status() {
        return last;
    }

    /** Counting must call this first: refuses to proceed when verification failed. */
    public void requireIntact() {
        BallotBox.Status s = box.verify();
        last = s;
        if (!s.ok()) {
            throw new IllegalStateException("ballot box failed verification: counting refused");
        }
    }
}
