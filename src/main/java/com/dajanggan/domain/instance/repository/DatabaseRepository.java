package com.dajanggan.domain.instance.repository;

import com.dajanggan.domain.instance.domain.Database;
import com.dajanggan.domain.instance.dto.DatabaseResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DatabaseRepository {
    List<DatabaseResponse> findByInstanceId(@Param("instanceId") Long instanceId);

    // Entity 반환 (내부 로직용)
    List<Database> findDatabaseEntitiesByInstanceId(@Param("instanceId") Long instanceId);

    // N+1 방지: 여러 인스턴스의 DB를 한 번에 조회
    List<Database> findByInstanceIds(List<Long> ids);

    List<Database> findAllEnabled();

    // Database 삽입
    void insert(Database database);

    void deleteByInstanceId(@Param("instanceId") Long instanceId);
}
