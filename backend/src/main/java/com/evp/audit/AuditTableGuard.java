package com.evp.audit;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class AuditTableGuard {

    private static final Logger log = LoggerFactory.getLogger(AuditTableGuard.class);

    private final DataSource dataSource;

    public AuditTableGuard(DataSource dataSource, EntityManagerFactory tablesCreated) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    void protectAuditTable() throws SQLException {
        String database;
        try (Connection c = dataSource.getConnection()) {
            database = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        }
        ClassPathResource script = new ClassPathResource("db/audit/append-only-" + database + ".sql");
        if (!script.exists()) {
            throw new IllegalStateException("No append-only audit script for database '" + database + "'");
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(script);
        populator.setSeparator(";;"); 
        populator.execute(dataSource);
        log.info("Audit table is append-only ({})", script.getFilename());
    }
}