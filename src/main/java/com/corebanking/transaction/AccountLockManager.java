package com.corebanking.transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class AccountLockManager {
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID is required for locking.");
        }
        return locks.computeIfAbsent(accountId, ignored -> new ReentrantLock());
    }

    public void lockAccount(String accountId) {
        getLock(accountId).lock();
    }

    public void unlockAccount(String accountId) {
        getLock(accountId).unlock();
    }

    public LockScope lockAccounts(String firstAccountId, String secondAccountId) {
        List<String> accountIds = new ArrayList<>(List.of(firstAccountId, secondAccountId));
        Collections.sort(accountIds);
        if (accountIds.get(0).equals(accountIds.get(1))) {
            throw new IllegalArgumentException("Account IDs must be different.");
        }

        ReentrantLock firstLock = getLock(accountIds.get(0));
        ReentrantLock secondLock = getLock(accountIds.get(1));
        firstLock.lock();
        try {
            secondLock.lock();
        } catch (RuntimeException exception) {
            firstLock.unlock();
            throw exception;
        }
        return new LockScope(firstLock, secondLock);
    }

    public int registeredLockCount() {
        return locks.size();
    }

    public static final class LockScope implements AutoCloseable {
        private final ReentrantLock firstLock;
        private final ReentrantLock secondLock;
        private boolean closed;

        private LockScope(ReentrantLock firstLock, ReentrantLock secondLock) {
            this.firstLock = firstLock;
            this.secondLock = secondLock;
        }

        @Override
        public void close() {
            if (!closed) {
                secondLock.unlock();
                firstLock.unlock();
                closed = true;
            }
        }
    }
}
