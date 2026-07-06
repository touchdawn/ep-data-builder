package com.ep.databuilder.log;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Long> {

    List<OperationLogEntity> findByBizTypeAndBizIdOrderByIdDesc(String bizType, Long bizId);
}
