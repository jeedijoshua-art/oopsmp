package com.corebanking.config;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseTransaction implements AutoCloseable {
    private final Connection connection;
    private final boolean previousAutoCommit;
    private boolean completed;

    public DatabaseTransaction(Connection connection) throws SQLException {
        this.connection = connection;
        this.previousAutoCommit = connection.getAutoCommit();
        begin();
    }

    public void begin() throws SQLException {
        connection.setAutoCommit(false);
    }

    public void commit() throws SQLException {
        connection.commit();
        completed = true;
    }

    public void rollback() throws SQLException {
        connection.rollback();
        completed = true;
    }

    @Override
    public void close() throws SQLException {
        try {
            if (!completed) {
                connection.rollback();
            }
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}