package com.dajanggan.domain.event.dto;

public enum EventType {
        // 세션
        DEADLOCK,
        LOCK_WAIT,
        LONG_TRANSACTION,
        CONNECTION_HIGH_USAGE,
        TOO_MANY_WAITING,
        IDLE_IN_TRANSACTION_SURGE,

        // 쿼리
        SLOW_QUERY,

}
