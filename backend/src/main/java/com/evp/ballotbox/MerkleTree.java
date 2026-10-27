package com.evp.ballotbox;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RFC 6962 / RFC 9162 Merkle tree. Domain separation: leaf = H(0x00 || data), node = H(0x01 || left || right),
 * so a leaf can never be passed off as an interior node (second-preimage defence).
 */
public final class MerkleTree {
    public static final byte[] EMPTY_ROOT = sha256(new byte[0]);

    private MerkleTree() { }

    static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) {
                d.update(p);
            }
            return d.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] leafHash(byte[] data) {
        return sha256(new byte[] {0}, data);
    }

    public static byte[] nodeHash(byte[] left, byte[] right) {
        return sha256(new byte[] {1}, left, right);
    }

    /** Largest power of two strictly less than n (n > 1). */
    private static int split(int n) {
        int k = 1;
        while (k * 2 < n) {
            k *= 2;
        }
        return k;
    }

    /** Merkle tree hash of already leaf-hashed entries. */
    public static byte[] root(List<byte[]> leaves) {
        int n = leaves.size();
        if (n == 0) {
            return EMPTY_ROOT;
        }
        if (n == 1) {
            return leaves.get(0);
        }
        int k = split(n);
        return nodeHash(root(leaves.subList(0, k)), root(leaves.subList(k, n)));
    }

    /** Audit path for leaf m (0-based) in a tree of the given leaves. */
    public static List<byte[]> inclusionPath(int m, List<byte[]> leaves) {
        int n = leaves.size();
        if (n == 1) {
            return new ArrayList<>();
        }
        int k = split(n);
        List<byte[]> out;
        if (m < k) {
            out = inclusionPath(m, leaves.subList(0, k));
            out.add(root(leaves.subList(k, n)));
        } else {
            out = inclusionPath(m - k, leaves.subList(k, n));
            out.add(root(leaves.subList(0, k)));
        }
        return out;
    }

    public static boolean verifyInclusion(byte[] leaf, long index, long size, List<byte[]> path, byte[] root) {
        if (index >= size) {
            return false;
        }
        long fn = index;
        long sn = size - 1;
        byte[] r = leaf;
        for (byte[] p : path) {
            if (sn == 0) {
                return false;
            }
            if ((fn & 1) == 1 || fn == sn) {
                r = nodeHash(p, r);
                if ((fn & 1) == 0) {
                    while ((fn & 1) == 0 && fn != 0) {
                        fn >>= 1;
                        sn >>= 1;
                    }
                }
            } else {
                r = nodeHash(r, p);
            }
            fn >>= 1;
            sn >>= 1;
        }
        return sn == 0 && Arrays.equals(r, root);
    }

    /** Proof that the tree of the first m leaves is a prefix of the tree of all given leaves. */
    public static List<byte[]> consistencyProof(int m, List<byte[]> leaves) {
        return subProof(m, leaves, true);
    }

    private static List<byte[]> subProof(int m, List<byte[]> d, boolean b) {
        int n = d.size();
        List<byte[]> out = new ArrayList<>();
        if (m == n) {
            if (!b) {
                out.add(root(d));
            }
            return out;
        }
        int k = split(n);
        if (m <= k) {
            out = subProof(m, d.subList(0, k), b);
            out.add(root(d.subList(k, n)));
        } else {
            out = subProof(m - k, d.subList(k, n), false);
            out.add(root(d.subList(0, k)));
        }
        return out;
    }

    public static boolean verifyConsistency(long first, long second, byte[] firstRoot, byte[] secondRoot, List<byte[]> proof) {
        if (first < 1 || first > second) {
            return false;
        }
        if (first == second) {
            return proof.isEmpty() && Arrays.equals(firstRoot, secondRoot);
        }
        List<byte[]> path = new ArrayList<>();
        if ((first & (first - 1)) == 0) {
            path.add(firstRoot);
        }
        path.addAll(proof);
        if (path.isEmpty()) {
            return false;
        }
        long fn = first - 1;
        long sn = second - 1;
        while ((fn & 1) == 1) {
            fn >>= 1;
            sn >>= 1;
        }
        byte[] fr = path.get(0);
        byte[] sr = path.get(0);
        for (byte[] c : path.subList(1, path.size())) {
            if (sn == 0) {
                return false;
            }
            if ((fn & 1) == 1 || fn == sn) {
                fr = nodeHash(c, fr);
                sr = nodeHash(c, sr);
                if ((fn & 1) == 0) {
                    while ((fn & 1) == 0 && fn != 0) {
                        fn >>= 1;
                        sn >>= 1;
                    }
                }
            } else {
                sr = nodeHash(sr, c);
            }
            fn >>= 1;
            sn >>= 1;
        }
        return Arrays.equals(fr, firstRoot) && Arrays.equals(sr, secondRoot) && sn == 0;
    }
}
