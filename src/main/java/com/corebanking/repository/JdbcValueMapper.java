package com.corebanking.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

final class JdbcValueMapper {
    private JdbcValueMapper() {
    }

    static BigDecimal readMoney(ResultSet resultSet, String columnName) throws SQLException {
        String value = resultSet.getString(columnName);
        return value == null ? null : new BigDecimal(value);
    }

    static LocalDateTime readDateTime(ResultSet resultSet, String columnName) throws SQLException {
        String value = resultSet.getString(columnName);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            return LocalDateTime.parse(value.replace(' ', 'T'));
        }
    }

    static String writeDateTime(LocalDateTime value) {
        return value.toString();
    }
}
