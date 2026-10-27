package com.evp.secrecy;

import java.security.SecureRandom;
import java.util.HexFormat;

/** Random 128-bit receipt: the ONLY identifier on a ballot row. It is never stored on the identity side. */
public final class Receipts {
    private static final SecureRandom RNG = new SecureRandom();

    private Receipts() { }

    public static String newReceipt() {
        byte[] b = new byte[16];
        RNG.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
