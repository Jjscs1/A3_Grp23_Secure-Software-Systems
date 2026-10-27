package com.evp.secrecy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Real database check: separate schemas, separate database users, minimal grants, no link between them.
 * (H2 stands in for PostgreSQL; production grants are in db/sec13-ballot-separation.sql.)
 */
class BallotSeparationTest {
    static final String URL = "jdbc:h2:mem:sec13;DB_CLOSE_DELAY=-1";
    static final String ROLE_URL = "jdbc:h2:mem:sec13"; // non-admin users may not pass DB_CLOSE_DELAY
    static final String PW = "test-only-not-a-secret"; // throw-away in-memory database

    static DataSource as(String user) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(ROLE_URL); // the admin connection below keeps the in-memory database alive
        ds.setUser(user);
        ds.setPassword(PW);
        return ds;
    }

    @BeforeAll
    static void createDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA identity");
            s.execute("CREATE TABLE identity.voter (id BIGINT PRIMARY KEY, lookup_key VARCHAR(64), division VARCHAR(60), has_voted BOOLEAN)");
            s.execute("CREATE SCHEMA " + BallotSchema.SCHEMA);
            s.execute(BallotSchema.CREATE_TABLE);
            s.execute("CREATE USER identity_app PASSWORD '" + PW + "'");
            s.execute("CREATE USER ballot_writer PASSWORD '" + PW + "'");
            s.execute("CREATE USER ballot_counter PASSWORD '" + PW + "'");
            s.execute("GRANT SELECT, INSERT, UPDATE ON identity.voter TO identity_app");
            s.execute("GRANT INSERT ON " + BallotSchema.TABLE + " TO ballot_writer");
            s.execute("GRANT SELECT ON " + BallotSchema.TABLE + " TO ballot_counter");
            s.execute("INSERT INTO identity.voter VALUES (1, 'abc', 'Melbourne', TRUE)");
        }
    }

    @AfterAll
    static void drop() throws SQLException {
        try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
        }
    }

    private static Clock at(String iso) {
        return Clock.fixed(Instant.parse(iso), ZoneOffset.UTC);
    }

    @Test
    void ballotTableHasNoIdentityColumnsAndNoForeignKeys() throws SQLException {
        List<String> cols = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_SCHEMA='BALLOTS' AND TABLE_NAME='BALLOT' ORDER BY ORDINAL_POSITION")) {
                while (rs.next()) {
                    cols.add(rs.getString(1).toLowerCase());
                }
            }
            assertEquals(BallotSchema.COLUMNS, cols);
            for (String c2 : cols) {
                for (String part : c2.split("_")) {
                    assertFalse(Set.of("user", "voter", "person", "ip", "session", "account", "identity", "login").contains(part), c2);
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE CONSTRAINT_TYPE='FOREIGN KEY' AND (TABLE_SCHEMA='BALLOTS' OR TABLE_SCHEMA='IDENTITY')")) {
                rs.next();
                assertEquals(0, rs.getInt(1), "no foreign key may join identity and ballots in either direction");
            }
        }
    }

    @Test
    void writerCanOnlyInsert_andStoresTheHourNotTheMinute() throws Exception {
        BallotWriter w = new BallotWriter(as("BALLOT_WRITER"), at("2026-10-27T11:37:42.123Z"));
        String receipt = w.insert("Melbourne", "{\"sealed\":true}");
        assertTrue(receipt.matches("[0-9a-f]{32}"));
        try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT cast_hour, division FROM " + BallotSchema.TABLE + " WHERE receipt='" + receipt + "'")) {
            assertTrue(rs.next());
            assertEquals("2026-10-27T11:00Z", rs.getString(1));
            assertEquals("Melbourne", rs.getString(2));
        }
        // the writer role cannot read, change or delete what it wrote
        try (Connection c = as("BALLOT_WRITER").getConnection(); Statement s = c.createStatement()) {
            assertThrows(SQLException.class, () -> s.executeQuery("SELECT * FROM " + BallotSchema.TABLE));
            assertThrows(SQLException.class, () -> s.executeUpdate("UPDATE " + BallotSchema.TABLE + " SET division='x'"));
            assertThrows(SQLException.class, () -> s.executeUpdate("DELETE FROM " + BallotSchema.TABLE));
        }
    }

    @Test
    void counterCanOnlyRead() throws Exception {
        new BallotWriter(as("BALLOT_WRITER"), at("2026-10-27T09:00:00Z")).insert("Counterville", "{\"a\":1}");
        try (Connection c = as("BALLOT_COUNTER").getConnection(); Statement s = c.createStatement()) {
            assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO " + BallotSchema.TABLE
                    + " VALUES ('x','d','p','h')"));
            assertThrows(SQLException.class, () -> s.executeUpdate("DELETE FROM " + BallotSchema.TABLE));
            assertThrows(SQLException.class, () -> s.executeQuery("SELECT * FROM identity.voter"));
        }
        assertEquals(List.of("{\"a\":1}"), new BallotReader(as("BALLOT_COUNTER")).readForCount("Counterville"));
    }

    @Test
    void identityRoleCannotTouchBallots_andBallotRolesCannotTouchIdentity() throws Exception {
        try (Connection c = as("IDENTITY_APP").getConnection(); Statement s = c.createStatement()) {
            assertTrue(s.executeQuery("SELECT * FROM identity.voter").next()); // its own data works
            assertThrows(SQLException.class, () -> s.executeQuery("SELECT * FROM " + BallotSchema.TABLE));
            assertThrows(SQLException.class, () -> s.executeUpdate("INSERT INTO " + BallotSchema.TABLE + " VALUES ('x','d','p','h')"));
        }
        for (String role : List.of("BALLOT_WRITER", "BALLOT_COUNTER")) {
            try (Connection c = as(role).getConnection(); Statement s = c.createStatement()) {
                assertThrows(SQLException.class, () -> s.executeQuery("SELECT * FROM identity.voter"), role);
            }
        }
    }

    @Test
    void noRoleHoldsPrivilegesOnBothSchemas() throws SQLException {
        Set<String> onIdentity = new HashSet<>(), onBallots = new HashSet<>();
        try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT GRANTEE, TABLE_SCHEMA FROM INFORMATION_SCHEMA.TABLE_PRIVILEGES")) {
            while (rs.next()) {
                (rs.getString(2).equals("IDENTITY") ? onIdentity : onBallots).add(rs.getString(1));
            }
        }
        onIdentity.remove("SA");
        onBallots.remove("SA");
        assertFalse(onIdentity.isEmpty());
        assertFalse(onBallots.isEmpty());
        onIdentity.retainAll(onBallots);
        assertEquals(Set.of(), onIdentity, "a credential with access to both schemas could re-link voters to ballots");
    }

    @Test
    void countingOrderIsRandomised() throws Exception {
        BallotWriter w = new BallotWriter(as("BALLOT_WRITER"), at("2026-10-27T10:00:00Z"));
        List<String> inserted = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String p = "{\"n\":" + i + "}";
            inserted.add(p);
            w.insert("Shuffleton", p);
        }
        Set<List<String>> orders = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            List<String> got = new BallotReader(as("BALLOT_COUNTER")).readForCount("Shuffleton");
            assertEquals(new HashSet<>(inserted), new HashSet<>(got)); // nothing lost or added
            orders.add(got);
        }
        assertTrue(orders.size() > 1, "the counting order must not be stable");
        // and it is the injected RNG that decides the order (so tests are reproducible)
        List<String> a = new BallotReader(as("BALLOT_COUNTER"), new Random(1)).readForCount("Shuffleton");
        List<String> b = new BallotReader(as("BALLOT_COUNTER"), new Random(1)).readForCount("Shuffleton");
        assertEquals(a, b);
        assertNotEquals(inserted, a);
    }

    @Test
    void divisionsAreKeptApart() throws Exception {
        BallotWriter w = new BallotWriter(as("BALLOT_WRITER"), at("2026-10-27T10:00:00Z"));
        w.insert("North", "{\"d\":\"n\"}");
        w.insert("South", "{\"d\":\"s\"}");
        assertEquals(List.of("{\"d\":\"n\"}"), new BallotReader(as("BALLOT_COUNTER")).readForCount("North"));
    }
}
