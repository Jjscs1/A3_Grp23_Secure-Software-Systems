package com.evp.secrecy;

import java.util.List;

/**
 * SEC-13: the ballot table lives in its OWN schema ("ballots"), apart from identity data.
 * It holds a random receipt, the division, the sealed envelope and the cast time to the hour.
 * There is deliberately NO voter id, user id, session, IP or foreign key anywhere in it.
 */
public final class BallotSchema {
    public static final String SCHEMA = "ballots";
    public static final String TABLE = SCHEMA + ".ballot";
    public static final List<String> COLUMNS = List.of("receipt", "division", "payload", "cast_hour");

    /** Portable DDL (PostgreSQL and H2). Same statement is used by db/sec13-ballot-separation.sql. */
    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE + " ("
            + "receipt VARCHAR(32) PRIMARY KEY, "
            + "division VARCHAR(60) NOT NULL, "
            + "payload VARCHAR(8192) NOT NULL, "
            + "cast_hour VARCHAR(17) NOT NULL)";

    private BallotSchema() { }
}
