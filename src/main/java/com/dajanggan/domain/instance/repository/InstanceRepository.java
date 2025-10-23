package com.dajanggan.domain.instance.repository;

import com.dajanggan.domain.instance.domain.Instance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InstanceRepository {
    void insertInstance(Instance instance);

}



