package com.evp.ballotbox;

import java.util.ArrayList;
import java.util.List;

/** Non-persistent store for tests and demos. */
public class MemoryLogStore implements LogStore {
    final List<String> leaves = new ArrayList<>();
    final List<Checkpoint> checkpoints = new ArrayList<>();
    final List<String> pending = new ArrayList<>();

    @Override public synchronized List<String> leaves() { return new ArrayList<>(leaves); }
    @Override public synchronized void appendLeaves(List<String> l) { leaves.addAll(l); }
    @Override public synchronized List<Checkpoint> checkpoints() { return new ArrayList<>(checkpoints); }
    @Override public synchronized void saveCheckpoint(Checkpoint c) { checkpoints.add(c); }
    @Override public synchronized void addPending(String leafHex) { pending.add(leafHex); }
    @Override public synchronized List<String> takePending() {
        List<String> p = new ArrayList<>(pending);
        pending.clear();
        return p;
    }
}
