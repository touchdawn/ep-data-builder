package com.ep.databuilder.factory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FactoryParamRepository extends JpaRepository<FactoryParamEntity, Long> {

    List<FactoryParamEntity> findByFactoryIdOrderBySortNo(Long factoryId);

    void deleteByFactoryId(Long factoryId);
}
