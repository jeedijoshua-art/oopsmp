package com.corebanking.ui.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class FormatUtil {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private FormatUtil() {
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale.Builder()
            .setLanguage("en")
            .setRegion("IN")
            .build());
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME.format(value);
    }
}
