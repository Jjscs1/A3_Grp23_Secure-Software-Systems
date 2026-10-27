package com.evp.trustee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.security.SecureRandom;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShamirTest {

    private static byte[] randomSecret() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    @Test
    @DisplayName("SEC-15: 2 shares fail")
    void twoSharesFail() {
        List<String> shares = Shamir.split(randomSecret(), 3, 5);
        assertThatThrownBy(() -> Shamir.combine(shares.subList(0, 2), 3))
                .isInstanceOf(NotEnoughSharesException.class)
                .hasMessageContaining("At least 3");
    }

    @Test
    @DisplayName("SEC-15: every combination of 3 of the 5 shares rebuilds the secret")
    void anyThreeOfFiveSucceed() {
        byte[] secret = randomSecret();
        List<String> shares = Shamir.split(secret, 3, 5);
        int combinations = 0;
        for (int a = 0; a < 5; a++) {
            for (int b = a + 1; b < 5; b++) {
                for (int c = b + 1; c < 5; c++) {
                    assertThat(Shamir.combine(List.of(shares.get(a), shares.get(b), shares.get(c)), 3))
                            .isEqualTo(secret);
                    combinations++;
                }
            }
        }
        assertThat(combinations).isEqualTo(10);
    }

    @Test
    @DisplayName("SEC-15: all 5 shares also work")
    void allFiveSucceed() {
        byte[] secret = randomSecret();
        assertThat(Shamir.combine(Shamir.split(secret, 3, 5), 3)).isEqualTo(secret);
    }

    @Test
    @DisplayName("SEC-15: the same share entered three times counts as one")
    void duplicateShareCountsOnce() {
        List<String> shares = Shamir.split(randomSecret(), 3, 5);
        assertThatThrownBy(() -> Shamir.combine(List.of(shares.get(0), shares.get(0), shares.get(0)), 3))
                .isInstanceOf(NotEnoughSharesException.class);
    }

    @Test
    @DisplayName("SEC-15: a typo in a share is caught by its checksum")
    void typoIsCaught() {
        List<String> shares = Shamir.split(randomSecret(), 3, 5);
        String share = shares.get(0);
        char last = share.charAt(20);
        String typo = share.substring(0, 20) + (last == 'a' ? 'b' : 'a') + share.substring(21);
        assertThatThrownBy(() -> Shamir.combine(List.of(typo, shares.get(1), shares.get(2)), 3))
                .isInstanceOf(ShareException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    @DisplayName("SEC-15: shares from a different ceremony give a different (useless) secret")
    void sharesFromAnotherCeremonyDoNotMix() {
        byte[] secret = randomSecret();
        List<String> ours = Shamir.split(secret, 3, 5);
        List<String> theirs = Shamir.split(randomSecret(), 3, 5);
        assertThat(Shamir.combine(List.of(ours.get(0), ours.get(1), theirs.get(2)), 3)).isNotEqualTo(secret);
    }
}