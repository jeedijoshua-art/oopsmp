package com.corebanking.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class IdGenerator {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private IdGenerator() {
    }

    public static String nextTransactionId() {
        return "TXN-" + LocalDate.now().format(DATE_FORMAT) + "-" + UUID.randomUUID();
    }
}
