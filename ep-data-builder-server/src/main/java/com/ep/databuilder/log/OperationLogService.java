package com.ep.databuilder.log;

import com.ep.databuilder.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public void record(String bizType, Long bizId, String action, String detail) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setAction(action);
        entity.setDetail(detail);
        entity.setOperator(UserContext.usernameOrSystem());
        operationLogRepository.save(entity);
    }
}
