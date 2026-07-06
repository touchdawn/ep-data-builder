package com.ep.databuilder.factory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FactoryStepRepository extends JpaRepository<FactoryStepEntity, Long> {

    List<FactoryStepEntity> findByFactoryIdOrderBySortNo(Long factoryId);

    void deleteByFactoryId(Long factoryId);
}
