package com.evp.validation;

import static com.evp.validation.InputRules.TextKind.ADDRESS;
import static com.evp.validation.InputRules.TextKind.NAME;
import static com.evp.validation.InputRules.TextKind.ORGANISATION;
import static com.evp.validation.InputRules.TextKind.REFERENCE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InputRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"Jane Citizen", "Sean O'Brien", "Jos\u00e9 N\u00fa\u00f1ez-Li", "Mary-Anne Smith Jr.", "\u5f20\u4f1f"})
    void realNamesAccepted(String n) {
        assertTrue(InputRules.check(NAME, n, 1, 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>alert(1)</script>", "<img src=x onerror=alert(1)>", "javascript:alert(1)",
            "\"><svg/onload=alert(1)>", "'; DROP TABLE voter;--", "admin'--", "Robert'); DROP TABLE x;", "' OR '1'='1",
            "1; SELECT * FROM voter", "Jane\u0000Citizen", "Jane\r\nCitizen", "a/*b*/c", "${jndi:ldap://x}", "{{7*7}}"})
    void injectionPayloadsRejectedInNames(String p) {
        assertFalse(InputRules.check(NAME, p, 1, 100), p);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<b>x</b>", "1 Main St; DROP TABLE x", "12 Main St\n", "a--b Street", "x=1&y=2", "(select 1)"})
    void injectionPayloadsRejectedInAddresses(String p) {
        assertFalse(InputRules.check(ADDRESS, p, 5, 200), p);
    }

    @ParameterizedTest
    @ValueSource(strings = {"42 Wallaby Way, Melbourne", "Unit 3/12 O'Connell St.", "5-7 High St #2"})
    void realAddressesAccepted(String a) {
        assertTrue(InputRules.check(ADDRESS, a, 5, 200));
    }

    @Test
    void lengthBoundsAreEnforcedAfterStripping() {
        assertFalse(InputRules.check(NAME, "A", 2, 100));
        assertTrue(InputRules.check(NAME, "  Al  ", 2, 100));
        assertFalse(InputRules.check(NAME, "A".repeat(101), 1, 100));
        assertFalse(InputRules.check(NAME, null, 1, 100));
        assertFalse(InputRules.check(NAME, "   ", 1, 100));
    }

    @Test
    void organisationsAndReferences() {
        assertTrue(InputRules.check(ORGANISATION, "Green & Co. Party", 1, 100));
        assertFalse(InputRules.check(ORGANISATION, "<b>Party</b>", 1, 100));
        assertTrue(InputRules.check(REFERENCE, "HCA 12/2026", 3, 40));
        assertFalse(InputRules.check(REFERENCE, "HCA';--", 3, 40));
    }

    @Test
    void controlCharactersAndSqlComments() {
        assertTrue(InputRules.hasControlChars("a\u0000b"));
        assertTrue(InputRules.hasControlChars("a\u007fb"));
        assertTrue(InputRules.hasControlChars("a\u0085b"));
        assertFalse(InputRules.hasControlChars("plain text"));
        assertTrue(InputRules.hasSqlCommentToken("x--y"));
        assertFalse(InputRules.hasSqlCommentToken("Smith-Jones"));
    }

    @Test
    void datesMustBeRealAndNotInTheFuture() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        assertTrue(InputRules.isIsoPastDate("1990-01-01", today));
        assertTrue(InputRules.isIsoPastDate("2026-10-27", today));
        assertFalse(InputRules.isIsoPastDate("2026-10-28", today));
        assertFalse(InputRules.isIsoPastDate("2020-02-31", today));
        assertFalse(InputRules.isIsoPastDate("1850-01-01", today));
        assertFalse(InputRules.isIsoPastDate("01/01/1990", today));
        assertFalse(InputRules.isIsoPastDate("1990-1-1", today));
        assertFalse(InputRules.isIsoPastDate("1990-01-01' OR 1=1", today));
        assertFalse(InputRules.isIsoPastDate(null, today));
    }

    @Test
    void postcodes() {
        assertTrue(InputRules.isPostcode("3000"));
        assertFalse(InputRules.isPostcode("300"));
        assertFalse(InputRules.isPostcode("30000"));
        assertFalse(InputRules.isPostcode("3O00"));
        assertFalse(InputRules.isPostcode("3000 "));
        assertFalse(InputRules.isPostcode(null));
    }
}
