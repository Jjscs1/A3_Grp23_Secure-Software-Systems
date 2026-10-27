package com.evp.secrecy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import javax.sql.DataSource;

/**
 * INSERT-only access to the ballot schema. Use a DataSource that connects as the database role
 * "ballot_writer", which holds INSERT and nothing else. The cast time is coarsened HERE, so callers
 * cannot supply anything finer, and no voter identifier can be passed in.
 */
public final class BallotWriter {
    private static final String INSERT =
            "INSERT INTO " + BallotSchema.TABLE + " (receipt, division, payload, cast_hour) VALUES (?, ?, ?, ?)";

    private final DataSource writerSource;
    private final Clock clock;

    public BallotWriter(DataSource writerSource, Clock clock) {
        this.writerSource = writerSource;
        this.clock = clock;
    }

    /** Stores one sealed ballot and returns its random receipt. */
    public String insert(String division, String sealedEnvelopeJson) throws SQLException {
        String receipt = Receipts.newReceipt();
        try (Connection c = writerSource.getConnection(); PreparedStatement ps = c.prepareStatement(INSERT)) {
            ps.setString(1, receipt);
            ps.setString(2, division);
            ps.setString(3, sealedEnvelopeJson);
            ps.setString(4, HourCoarsener.toHour(clock.instant()));
            ps.executeUpdate();
        }
        return receipt;
    }
}
