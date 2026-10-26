package com.evp.enrolment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** SEC-08: exact match key. */
class EnrolmentLookupKeyTest {

    private final EnrolmentLookupKey key = new EnrolmentLookupKey(new byte[32]);
    private final LocalDate dob = LocalDate.of(1990, 1, 1);

    @Test
    void sameDetailsWithDifferentSpacingAndCaseMatch() {
        assertThat(key.compute("  jane   SMITH ", dob, " 3000 "))
                .isEqualTo(key.compute("Jane Smith", dob, "3000"));
    }

    @Test
    void partialMatchesProduceDifferentKeys() {
        String full = key.compute("Jane Smith", dob, "3000");
        assertThat(key.compute("Jane Smit", dob, "3000")).isNotEqualTo(full);
        assertThat(key.compute("Jane Smith", dob, "3001")).isNotEqualTo(full);
        assertThat(key.compute("Jane Smith", dob.plusDays(1), "3000")).isNotEqualTo(full);
    }

    @Test
    void differentSecretGivesDifferentKey() {
        byte[] other = new byte[32];
        other[0] = 1;
        assertThat(new EnrolmentLookupKey(other).compute("Jane Smith", dob, "3000"))
                .isNotEqualTo(key.compute("Jane Smith", dob, "3000"));
    }

    @Test
    void shortSecretRejected() {
        assertThatThrownBy(() -> new EnrolmentLookupKey(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
