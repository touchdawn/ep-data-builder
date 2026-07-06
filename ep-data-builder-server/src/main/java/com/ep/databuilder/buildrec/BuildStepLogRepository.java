package com.ep.databuilder.buildrec;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildStepLogRepository extends JpaRepository<BuildStepLogEntity, Long> {

    List<BuildStepLogEntity> findByBuildIdOrderBySortNo(Long buildId);
}
