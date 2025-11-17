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

        // vacuum
        Autovacuum_Worker_Utilization,
        Blockers_Per_Hour,
        Transaction_Age,
        Block_Duration,
        Wraparound_Progress,
        Total_Table_Bloat,
        Bloat_Percent,
        Dead_Tuples,
        Table_Size


}
