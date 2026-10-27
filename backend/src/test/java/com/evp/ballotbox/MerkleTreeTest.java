package com.evp.ballotbox;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerkleTreeTest {
    static List<byte[]> leaves(int n) {
        List<byte[]> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(MerkleTree.leafHash(("ballot-" + i).getBytes(StandardCharsets.UTF_8)));
        }
        return l;
    }

    @Test
    void emptyTreeRootIsHashOfEmptyString() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HexFormat.of().formatHex(MerkleTree.root(new ArrayList<>())));
    }

    @Test
    void rfc6962KnownVectorForEmptyLeaf() {
        // RFC 6962 test vector: leaf hash of the empty string
        assertEquals("6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d",
                HexFormat.of().formatHex(MerkleTree.leafHash(new byte[0])));
    }

    @Test
    void leafAndNodeAreDomainSeparated() {
        byte[] a = MerkleTree.leafHash("x".getBytes(StandardCharsets.UTF_8));
        byte[] b = MerkleTree.leafHash("y".getBytes(StandardCharsets.UTF_8));
        byte[] node = MerkleTree.nodeHash(a, b);
        byte[] concat = new byte[64];
        System.arraycopy(a, 0, concat, 0, 32);
        System.arraycopy(b, 0, concat, 32, 32);
        assertFalse(java.util.Arrays.equals(node, MerkleTree.leafHash(concat)));
    }

    @Test
    void everyInclusionProofVerifiesForSizes1to20() {
        for (int n = 1; n <= 20; n++) {
            List<byte[]> l = leaves(n);
            byte[] root = MerkleTree.root(l);
            for (int i = 0; i < n; i++) {
                List<byte[]> path = MerkleTree.inclusionPath(i, l);
                assertTrue(MerkleTree.verifyInclusion(l.get(i), i, n, path, root), "n=" + n + " i=" + i);
            }
        }
    }

    @Test
    void inclusionRejectsWrongLeafIndexSizeAndPath() {
        List<byte[]> l = leaves(13);
        byte[] root = MerkleTree.root(l);
        List<byte[]> path = MerkleTree.inclusionPath(5, l);
        assertTrue(MerkleTree.verifyInclusion(l.get(5), 5, 13, path, root));
        assertFalse(MerkleTree.verifyInclusion(l.get(6), 5, 13, path, root));
        assertFalse(MerkleTree.verifyInclusion(l.get(5), 4, 13, path, root));
        assertFalse(MerkleTree.verifyInclusion(l.get(5), 13, 13, path, root));
        List<byte[]> bad = new ArrayList<>(path);
        bad.set(0, MerkleTree.leafHash(new byte[] {9}));
        assertFalse(MerkleTree.verifyInclusion(l.get(5), 5, 13, bad, root));
        assertFalse(MerkleTree.verifyInclusion(l.get(5), 5, 13, path.subList(1, path.size()), root));
    }

    @Test
    void everyConsistencyProofVerifiesForAllPairsUpTo20() {
        for (int second = 1; second <= 20; second++) {
            List<byte[]> l = leaves(second);
            byte[] r2 = MerkleTree.root(l);
            for (int first = 1; first <= second; first++) {
                byte[] r1 = MerkleTree.root(l.subList(0, first));
                List<byte[]> proof = first == second ? new ArrayList<>() : MerkleTree.consistencyProof(first, l);
                assertTrue(MerkleTree.verifyConsistency(first, second, r1, r2, proof), first + "->" + second);
            }
        }
    }

    @Test
    void consistencyRejectsRewrittenHistory() {
        List<byte[]> l = leaves(10);
        byte[] r2 = MerkleTree.root(l);
        List<byte[]> forged = leaves(10);
        forged.set(2, MerkleTree.leafHash("evil".getBytes(StandardCharsets.UTF_8)));
        byte[] r1Forged = MerkleTree.root(forged.subList(0, 5));
        assertFalse(MerkleTree.verifyConsistency(5, 10, r1Forged, r2, MerkleTree.consistencyProof(5, l)));
    }
}
