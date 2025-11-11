package com.dajanggan.domain.instance.repository;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.dto.DatabaseResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DatabaseRepository {
    List<DatabaseResponse> findByInstanceId(@Param("instanceId") Long instanceId);

    // N+1 방지: 여러 인스턴스의 DB를 한 번에 조회
     List<Database> findByInstanceIds(List<Long> ids);

    // EXPLAIN ANALYZE용 단일 Database 조회
    Database findById(@Param("databaseId") Long databaseId);

}
