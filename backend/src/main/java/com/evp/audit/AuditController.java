package com.evp.audit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('AUDITOR')")
    public AuditService.Export export() {
        return service.export();
    }

    @GetMapping("/verify")
    @PreAuthorize("hasRole('AUDITOR')")
    public AuditChain.Verification verify(@RequestParam(required = false) Long anchorSeq,
                                          @RequestParam(required = false) String anchorHash) {
        return service.verify(anchorSeq, anchorHash);
    }
}