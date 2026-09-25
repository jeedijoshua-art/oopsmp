package com.corebanking.config;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseTransaction implements AutoCloseable {
    private final Connection connection;
    private boolean completed;

    public DatabaseTransaction(Connection connection) throws SQLException {
        this.connection = connection;
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
        if (!completed) {
            connection.rollback();
        }
        connection.setAutoCommit(true);
    }
}