package com.evp.ballotbox;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Append-only Merkle ballot log: accept, seal (signed checkpoint), prove, verify. */
public class BallotBox {
    private static final HexFormat HEX = HexFormat.of();

    private final LogStore store;
    private final CheckpointSigner signer;
    private final Clock clock;

    public BallotBox(LogStore store, CheckpointSigner signer, Clock clock) {
        this.store = store;
        this.signer = signer;
        this.clock = clock;
    }

    public SignerInfo signerInfo() {
        return signer.publicInfo();
    }

    /** Called when a ballot has been accepted. Returns the leaf the voter can look for. */
    public String accept(String receipt, String envelopeJson) {
        String leaf = BallotLeaf.leafHex(receipt, envelopeJson);
        store.addPending(leaf);
        return leaf;
    }

    /**
     * Moves pending ballots into the log and signs a new checkpoint. The batch is sorted by leaf hash, so the
     * position of a ballot reveals nothing about when it was cast (ballot secrecy, SEC-13).
     */
    public synchronized Checkpoint seal() throws java.security.GeneralSecurityException {
        List<String> batch = store.takePending();
        batch.sort(Comparator.naturalOrder());
        if (!batch.isEmpty()) {
            store.appendLeaves(batch);
        }
        List<String> all = store.leaves();
        Checkpoint unsigned = new Checkpoint(signer.alg(), signer.kid(), all.size(), HEX.formatHex(MerkleTree.root(decode(all))),
                Instant.now(clock).toString(), "");
        Checkpoint signed = new Checkpoint(unsigned.alg(), unsigned.kid(), unsigned.size(), unsigned.root(), unsigned.ts(),
                signer.sign(CheckpointSigner.message(unsigned)));
        store.saveCheckpoint(signed);
        return signed;
    }

    public Optional<Checkpoint> latest() {
        List<Checkpoint> cps = store.checkpoints();
        return cps.isEmpty() ? Optional.empty() : Optional.of(cps.get(cps.size() - 1));
    }

    public record InclusionProof(long index, long size, String leaf, List<String> path, Checkpoint checkpoint) { }

    public record ConsistencyProof(long first, long second, String firstRoot, String secondRoot, List<String> path) { }

    /** Inclusion proof against the latest checkpoint; empty if the leaf is not in the sealed log yet. */
    public Optional<InclusionProof> prove(String leafHex) {
        Optional<Checkpoint> cp = latest();
        if (cp.isEmpty()) {
            return Optional.empty();
        }
        List<String> all = store.leaves().subList(0, (int) cp.get().size());
        int idx = all.indexOf(leafHex);
        if (idx < 0) {
            return Optional.empty();
        }
        List<byte[]> path = MerkleTree.inclusionPath(idx, decode(all));
        return Optional.of(new InclusionProof(idx, all.size(), leafHex, encode(path), cp.get()));
    }

    public Optional<ConsistencyProof> consistency(long first, long second) {
        List<Checkpoint> cps = store.checkpoints();
        Optional<Checkpoint> a = cps.stream().filter(c -> c.size() == first).findFirst();
        Optional<Checkpoint> b = cps.stream().filter(c -> c.size() == second).findFirst();
        if (a.isEmpty() || b.isEmpty() || first < 1 || first > second) {
            return Optional.empty();
        }
        List<byte[]> leaves = decode(store.leaves().subList(0, (int) second));
        List<byte[]> proof = first == second ? new ArrayList<>() : MerkleTree.consistencyProof((int) first, leaves);
        return Optional.of(new ConsistencyProof(first, second, a.get().root(), b.get().root(), encode(proof)));
    }

    public record Status(boolean ok, long size, int checkpoints, String problem) {
        public static Status good(long size, int cps) { return new Status(true, size, cps, null); }
        public static Status bad(long size, int cps, String why) { return new Status(false, size, cps, why); }
    }

    /**
     * Re-checks everything from the stored data only: every checkpoint signature, every checkpoint root against
     * the leaves it claims to cover, and consistency between consecutive checkpoints. Any added, altered,
     * deleted or reordered ballot, or a forged or replaced checkpoint, makes it fail.
     */
    public synchronized Status verify() {
        List<String> all = store.leaves();
        List<Checkpoint> cps = store.checkpoints();
        String pub = signer.publicInfo().publicKey();
        Checkpoint prev = null;
        for (Checkpoint c : cps) {
            if (!CheckpointSigner.verify(pub, CheckpointSigner.message(c), c.sig())) {
                return Status.bad(all.size(), cps.size(), "checkpoint signature invalid at size " + c.size());
            }
            if (c.size() > all.size()) {
                return Status.bad(all.size(), cps.size(), "ballots missing: checkpoint covers " + c.size() + " but log has " + all.size());
            }
            String root = HEX.formatHex(MerkleTree.root(decode(all.subList(0, (int) c.size()))));
            if (!root.equals(c.root())) {
                return Status.bad(all.size(), cps.size(), "root mismatch at size " + c.size());
            }
            if (prev != null && prev.size() > 0) {
                if (c.size() < prev.size()) {
                    return Status.bad(all.size(), cps.size(), "log shrank");
                }
                List<byte[]> proof = prev.size() == c.size() ? new ArrayList<>()
                        : MerkleTree.consistencyProof((int) prev.size(), decode(all.subList(0, (int) c.size())));
                if (!MerkleTree.verifyConsistency(prev.size(), c.size(), HEX.parseHex(prev.root()), HEX.parseHex(c.root()), proof)) {
                    return Status.bad(all.size(), cps.size(), "history rewritten before size " + c.size());
                }
            }
            prev = c;
        }
        if (prev != null && prev.size() < all.size()) {
            return Status.bad(all.size(), cps.size(), "unsealed ballots in log beyond last checkpoint");
        }
        return Status.good(all.size(), cps.size());
    }

    static List<byte[]> decode(List<String> hex) {
        List<byte[]> out = new ArrayList<>(hex.size());
        for (String h : hex) {
            out.add(HEX.parseHex(h));
        }
        return out;
    }

    static List<String> encode(List<byte[]> bytes) {
        List<String> out = new ArrayList<>(bytes.size());
        for (byte[] b : bytes) {
            out.add(HEX.formatHex(b));
        }
        return out;
    }

    static boolean same(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}
