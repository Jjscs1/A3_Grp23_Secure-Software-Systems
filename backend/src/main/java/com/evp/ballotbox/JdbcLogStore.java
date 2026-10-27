package com.evp.ballotbox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * JDBC store over the tables in db/sec14-ballotbox.sql. Only SELECT and INSERT statements exist here,
 * and the SQL file additionally installs triggers that reject UPDATE/DELETE/TRUNCATE on the log tables.
 * All SQL is fixed text with bound parameters (SEC-06).
 */
public class JdbcLogStore implements LogStore {
    private final DataSource ds;

    public JdbcLogStore(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public List<String> leaves() {
        List<String> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement("SELECT leaf FROM ballotbox_leaf ORDER BY idx");
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(r.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ballot box read failed");
        }
        return out;
    }

    @Override
    public void appendLeaves(List<String> leaves) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO ballotbox_leaf (leaf) VALUES (?)")) {
                for (String l : leaves) {
                    p.setString(1, l);
                    p.addBatch();
                }
                p.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ballot box append failed");
        }
    }

    @Override
    public List<Checkpoint> checkpoints() {
        List<Checkpoint> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(
                "SELECT alg, kid, size, root, ts, sig FROM ballotbox_checkpoint ORDER BY size, id"); ResultSet r = p.executeQuery()) {
            while (r.next()) {
                out.add(new Checkpoint(r.getString(1), r.getString(2), r.getLong(3), r.getString(4), r.getString(5), r.getString(6)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ballot box read failed");
        }
        return out;
    }

    @Override
    public void saveCheckpoint(Checkpoint k) {
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO ballotbox_checkpoint (alg, kid, size, root, ts, sig) VALUES (?,?,?,?,?,?)")) {
            p.setString(1, k.alg());
            p.setString(2, k.kid());
            p.setLong(3, k.size());
            p.setString(4, k.root());
            p.setString(5, k.ts());
            p.setString(6, k.sig());
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("ballot box checkpoint failed");
        }
    }

    @Override
    public void addPending(String leafHex) {
        try (Connection c = ds.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO ballotbox_pending (leaf) VALUES (?)")) {
            p.setString(1, leafHex);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("ballot box accept failed");
        }
    }

    @Override
    public List<String> takePending() {
        List<String> out = new ArrayList<>();
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement("SELECT id, leaf FROM ballotbox_pending ORDER BY id");
                 ResultSet r = s.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (r.next()) {
                    ids.add(r.getLong(1));
                    out.add(r.getString(2));
                }
                try (PreparedStatement d = c.prepareStatement("DELETE FROM ballotbox_pending WHERE id = ?")) {
                    for (Long id : ids) {
                        d.setLong(1, id);
                        d.addBatch();
                    }
                    d.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("ballot box seal failed");
        }
        return out;
    }
}
