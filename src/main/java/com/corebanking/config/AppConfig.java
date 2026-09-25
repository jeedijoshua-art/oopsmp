package com.corebanking.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppConfig {
    private static final Path DATA_DIRECTORY = Paths.get("data");
    private static final Path DATABASE_FILE = DATA_DIRECTORY.resolve("core-banking.db");

    private AppConfig() {
    }

    public static Path databaseFile() {
        return DATABASE_FILE;
    }
}
