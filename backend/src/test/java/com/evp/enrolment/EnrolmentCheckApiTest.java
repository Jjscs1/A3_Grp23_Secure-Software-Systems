package com.evp.enrolment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evp.support.ApiTestBase;
import com.evp.voter.Voter;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** SEC-08 acceptance criteria through the real HTTP endpoint, without logging in. */
class EnrolmentCheckApiTest extends ApiTestBase {

    @Autowired
    private EnrolmentLookupKey lookupKey;

    @BeforeEach
    void enrolJane() {
        LocalDate dob = LocalDate.of(1990, 1, 1);
        voters.save(Voter.enrol("jane-sub", lookupKey.compute("Jane Smith", dob, "3000"), dob, true, "MELBOURNE"));
    }

    private MvcResult check(String name, String dob, String postcode) throws Exception {
        String body = """
                {"fullName":"%s","dateOfBirth":"%s","postcode":"%s"}""".formatted(name, dob, postcode);
        return mvc.perform(post("/api/public/enrolment-check").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    @Test
    @DisplayName("SEC-08: found and not-found responses have identical status and body shape")
    void identicalStatusAndShape() throws Exception {
        MvcResult found = check("Jane Smith", "1990-01-01", "3000");
        MvcResult notFound = check("John Doe", "1985-06-15", "2000");

        assertThat(found.getResponse().getStatus()).isEqualTo(200);
        assertThat(notFound.getResponse().getStatus()).isEqualTo(200);
        assertThat(found.getResponse().getContentAsString()).isEqualTo("{\"enrolled\":true}");
        assertThat(notFound.getResponse().getContentAsString()).isEqualTo("{\"enrolled\":false}");
        assertThat(found.getResponse().getHeaderNames()).isEqualTo(notFound.getResponse().getHeaderNames());
    }

    @Test
    @DisplayName("SEC-08: partial matches return not found")
    void partialMatchReturnsNotFound() throws Exception {
        assertThat(check("Jane Smit", "1990-01-01", "3000").getResponse().getContentAsString())
                .isEqualTo("{\"enrolled\":false}");
        assertThat(check("Jane Smith", "1990-01-01", "3001").getResponse().getContentAsString())
                .isEqualTo("{\"enrolled\":false}");
        assertThat(check("Jane Smith", "1990-01-02", "3000").getResponse().getContentAsString())
                .isEqualTo("{\"enrolled\":false}");
    }

    @Test
    @DisplayName("Name matching ignores case and extra spaces")
    void normalisedNameMatches() throws Exception {
        assertThat(check("  jane   SMITH ", "1990-01-01", "3000").getResponse().getContentAsString())
                .isEqualTo("{\"enrolled\":true}");
    }

    @Test
    @DisplayName("SEC-08: found and not-found take the same time, within 50 ms")
    void responseTimesWithin50ms() throws Exception {
        for (int i = 0; i < 3; i++) { // warm up class loading and the JIT before measuring
            check("Jane Smith", "1990-01-01", "3000");
            check("John Doe", "1985-06-15", "2000");
        }
        long[] found = new long[5];
        long[] notFound = new long[5];
        for (int i = 0; i < 5; i++) {
            found[i] = timeMillis(() -> check("Jane Smith", "1990-01-01", "3000"));
            notFound[i] = timeMillis(() -> check("John Doe", "1985-06-15", "2000"));
        }
        assertThat(Math.abs(median(found) - median(notFound))).isLessThan(50);
        assertThat(median(found)).isGreaterThanOrEqualTo(200); // the configured minimum
    }

    @Test
    @DisplayName("Malformed input is rejected without touching the roll")
    void malformedInputRejected() throws Exception {
        assertThat(check("<script>", "1990-01-01", "3000").getResponse().getStatus()).isEqualTo(400);
        assertThat(check("Jane Smith", "1990-01-01", "30000").getResponse().getStatus()).isEqualTo(400);
        assertThat(check("Jane Smith", "2999-01-01", "3000").getResponse().getStatus()).isEqualTo(400);
    }

    private interface Call {
        void run() throws Exception;
    }

    private static long timeMillis(Call call) throws Exception {
        long start = System.nanoTime();
        call.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
