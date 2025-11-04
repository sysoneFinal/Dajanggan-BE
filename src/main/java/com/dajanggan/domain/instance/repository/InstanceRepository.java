package com.dajanggan.domain.instance.repository;

import com.dajanggan.domain.instance.domain.Instance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface InstanceRepository {
    void insertInstance(Instance instance);

    Optional<Instance> findById(@Param("id") Long id);

    List<Instance> findAll();

    int updateInstance(Instance entity);

    int deleteById(@Param("id") Long id);

    boolean existsByName(@Param("instanceName") String instanceName);
}



