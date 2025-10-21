package com.dajanggan.domain.instance.repository;

import com.dajanggan.domain.instance.domain.Instance;
import com.dajanggan.domain.instance.dto.InstanceDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface InstanceRepository {
    int insertInstance(Instance instance);   // ✅ Entity로 통일 + insert 결과 row수 반환

}



