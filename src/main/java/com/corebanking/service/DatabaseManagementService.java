package com.corebanking.service;

public interface DatabaseManagementService {
    void resetDemoData();
    void resetEntireDatabase();
    int getDemoAccountCount();
    int getDemoTransactionCount();
}
