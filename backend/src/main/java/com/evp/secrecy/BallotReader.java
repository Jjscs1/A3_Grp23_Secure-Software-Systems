package com.evp.secrecy;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.sql.DataSource;

/**
 * SELECT-only access for counting. Use a DataSource that connects as the role "ballot_counter".
 * Ballots come back in a cryptographically random order, so any residual storage order
 * (insertion order) is destroyed before counting.
 */
public final class BallotReader {
    private static final String SELECT = "SELECT payload FROM " + BallotSchema.TABLE + " WHERE division = ?";

    private final DataSource counterSource;
    private final Random random;

    public BallotReader(DataSource counterSource) {
        this(counterSource, new SecureRandom());
    }

    BallotReader(DataSource counterSource, Random random) {
        this.counterSource = counterSource;
        this.random = random;
    }

    public List<String> readForCount(String division) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = counterSource.getConnection(); PreparedStatement ps = c.prepareStatement(SELECT)) {
            ps.setString(1, division);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        Collections.shuffle(out, random);
        return out;
    }
}
