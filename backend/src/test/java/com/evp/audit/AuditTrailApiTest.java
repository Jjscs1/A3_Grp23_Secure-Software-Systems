package com.evp.audit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.evp.support.ApiTestBase;
import com.evp.support.RecordingAuditLogger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class AuditTrailApiTest extends ApiTestBase {

    private static final List<String> APPLICATION_ROLES = List.of("evp_app", "evp_auditor", "evp_admin");

    @Autowired
    private HashChainAuditLogger auditLog;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditEntryRepository entries;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ApplicationContext context;

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    private Connection connectAs(String role) throws SQLException {
        return DriverManager.getConnection(databaseUrl.split(";")[0], role, "test-only");
    }

    private long writeEntry(String event) {
        auditLog.record(event, "delegate7", Map.of("ip", "10.0.2.14", "reason", "test"));
        return entries.findTopByOrderBySeqDesc().orElseThrow().getSeq();
    }

    @Test
    @DisplayName("SEC-17: UPDATE on the audit table fails for every application role")
    void updateFailsForEveryRole() throws SQLException {
        long seq = writeEntry("CANDIDATE_ADDED");
        for (String role : APPLICATION_ROLES) {
            try (Connection c = connectAs(role)) {
                assertThatThrownBy(() -> c.createStatement()
                        .executeUpdate("UPDATE audit_entry SET actor = 'mallory' WHERE seq = " + seq))
                        .as("UPDATE as %s", role).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    @DisplayName("SEC-17: DELETE on the audit table fails for every application role")
    void deleteFailsForEveryRole() throws SQLException {
        writeEntry("CANDIDATE_ADDED");
        for (String role : APPLICATION_ROLES) {
            try (Connection c = connectAs(role)) {
                assertThatThrownBy(() -> c.createStatement().executeUpdate("DELETE FROM audit_entry"))
                        .as("DELETE as %s", role).isInstanceOf(SQLException.class);
                assertThat(c.createStatement().executeQuery("SELECT COUNT(*) FROM audit_entry").next()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("SEC-17: even the table owner cannot UPDATE or DELETE (the trigger blocks it)")
    void ownerBlockedByTrigger() {
        long seq = writeEntry("CANDIDATE_ADDED");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_entry SET actor = 'mallory' WHERE seq = ?", seq))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_entry WHERE seq = ?", seq))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("append-only");
    }

    @Test
    @DisplayName("SEC-17: editing one row breaks hash-chain verification")
    void editingOneRowBreaksVerification() {
        long seq = writeEntry("BALLOT_CODE_ISSUED");
        writeEntry("LOGIN_SUCCESS");
        assertThat(auditService.verify(null, null).ok()).isTrue();

        String original = jdbc.queryForObject("SELECT details FROM audit_entry WHERE seq = ?", String.class, seq);
        jdbc.execute("DROP TRIGGER audit_entry_no_update");
        try {
            jdbc.update("UPDATE audit_entry SET details = ? WHERE seq = ?", "{\"ip\":\"somewhere-else\"}", seq);

            AuditChain.Verification v = auditService.verify(null, null);
            assertThat(v.ok()).isFalse();
            assertThat(v.brokenAt()).isEqualTo(seq);
            assertThat(v.problem()).contains("modified");
        } finally {
            jdbc.update("UPDATE audit_entry SET details = ? WHERE seq = ?", original, seq);
            jdbc.execute("CREATE TRIGGER audit_entry_no_update BEFORE UPDATE ON audit_entry "
                    + "FOR EACH ROW CALL 'com.evp.audit.H2AppendOnlyTrigger'");
        }
        assertThat(auditService.verify(null, null).ok()).isTrue();
    }

    @Test
    @DisplayName("SEC-17: a refused action is still recorded when the caller's transaction rolls back")
    void refusalSurvivesRollback() {
        long before = entries.count();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            auditLog.record("SELF_APPROVAL_REFUSED", "alice", Map.of("changeRequest", "5"));
            throw new IllegalStateException("caller fails and rolls back");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(entries.count()).isEqualTo(before + 1);
        assertThat(entries.findTopByOrderBySeqDesc().orElseThrow().getEventType()).isEqualTo("SELF_APPROVAL_REFUSED");
    }

    @Test
    @DisplayName("SEC-17: auditors can export and verify; other roles cannot")
    void auditorEndpoints() throws Exception {
        writeEntry("POLL_CLOSED");
        mvc.perform(get("/api/audit/verify").with(user("anao1", "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mvc.perform(get("/api/audit/export").with(user("anao1", "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKeyPem").value(org.hamcrest.Matchers.startsWith("-----BEGIN PUBLIC KEY")))
                .andExpect(jsonPath("$.entries[0].seq").value(1));
        mvc.perform(get("/api/audit/verify").with(user("voter1", "VOTER"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-17: the hash-chain logger is the app's only AuditLogger (Slf4jAuditLogger was removed)")
    void hashChainLoggerIsWiredIn() {
        Map<String, AuditLogger> loggers = context.getBeansOfType(AuditLogger.class);
        loggers.values().removeIf(l -> l instanceof RecordingAuditLogger); 
        assertThat(loggers.values()).hasSize(1).allMatch(l -> l instanceof HashChainAuditLogger);
    }
}