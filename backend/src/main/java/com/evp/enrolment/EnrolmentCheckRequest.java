package com.evp.enrolment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * SEC-08 request. Sent as a POST body so personal details never appear in URLs,
 * browser history or access logs. Validated against allow-lists (SEC-06).
 */
public record EnrolmentCheckRequest(
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "^[\\p{L} .'\\-]+$")
        String fullName,

        @NotNull
        @Past
        LocalDate dateOfBirth,

        @NotBlank
        @Pattern(regexp = "^\\s*\\d{4}\\s*$")
        String postcode) {
}
