package com.evp.ballotbox;

/** A signed tree head: how many ballots are in the log, and their Merkle root, signed by the ballot-box key. */
public record Checkpoint(String alg, String kid, long size, String root, String ts, String sig) {
}
