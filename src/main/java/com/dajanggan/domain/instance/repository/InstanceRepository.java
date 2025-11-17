package com.dajanggan.domain.instance.repository;

import com.dajanggan.domain.instance.domain.Instance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface InstanceRepository {
    void createInstance(Instance instance);
    Optional<Instance> findById(@Param("id") Long id);
    List<Instance> findAll();
    
    /**
     * 인스턴스 이름으로 ID 조회 (OS Metric Agent 자동 매핑용)
     */
    Optional<Long> findIdByInstanceName(@Param("instanceName") String instanceName);
    
    /**
     * 메트릭 수집을 위한 인스턴스 목록 조회 (secret_ref 포함)
     */
    List<Instance> findAllWithSecrets(@Param("instanceIds") List<Long> instanceIds);
    
    int updateInstance(Instance instance);
    void deleteById(@Param("id") Long id);
}
