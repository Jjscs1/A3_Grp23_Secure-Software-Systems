package com.evp.enrolment;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SEC-08: unauthenticated enrolment check. Permitted without login in SecurityConfig. */
@RestController
@RequestMapping("/api/public")
public class EnrolmentCheckController {

    private final EnrolmentCheckService service;

    public EnrolmentCheckController(EnrolmentCheckService service) {
        this.service = service;
    }

    @PostMapping("/enrolment-check")
    public EnrolmentCheckResponse check(@Valid @RequestBody EnrolmentCheckRequest request) {
        return service.check(request);
    }
}
