package com.evp.audit;
import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

public class H2AppendOnlyTrigger implements Trigger {

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException("audit_entry is append-only: "
                + (newRow == null ? "DELETE" : "UPDATE") + " is not allowed", "42501");
    }
}