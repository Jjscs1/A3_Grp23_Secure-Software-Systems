package com.evp.secrecy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HourCoarsenerTest {
    @Test
    void keepsOnlyTheHour() {
        assertEquals("2026-10-27T11:00Z", HourCoarsener.toHour(Instant.parse("2026-10-27T11:37:42.123Z")));
        assertEquals("2026-10-27T11:00Z", HourCoarsener.toHour(Instant.parse("2026-10-27T11:00:00Z")));
        assertEquals("2026-10-27T11:00Z", HourCoarsener.toHour(Instant.parse("2026-10-27T11:59:59.999Z")));
        assertEquals("2026-10-27T12:00Z", HourCoarsener.toHour(Instant.parse("2026-10-27T12:00:00Z")));
    }

    @Test
    void fitsTheColumnAndHasNoMinutesOrSeconds() {
        String s = HourCoarsener.toHour(Instant.now());
        assertTrue(s.length() <= 17);
        assertTrue(s.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:00Z"), s);
    }

    @Test
    void receiptsAreRandomHex() {
        String a = Receipts.newReceipt(), b = Receipts.newReceipt();
        assertTrue(a.matches("[0-9a-f]{32}"));
        assertTrue(!a.equals(b));
    }
}
