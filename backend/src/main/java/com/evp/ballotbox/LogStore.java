package com.evp.ballotbox;

import java.util.List;

/** Persistence for the log. Append-only by contract: there is no update or delete method. */
public interface LogStore {
    /** Leaf hashes (hex) in log order. */
    List<String> leaves();

    void appendLeaves(List<String> leafHexInOrder);

    List<Checkpoint> checkpoints();

    void saveCheckpoint(Checkpoint c);

    /** Accepted ballots waiting for the next seal. */
    void addPending(String leafHex);

    /** Returns and clears the pending leaves. */
    List<String> takePending();
}
