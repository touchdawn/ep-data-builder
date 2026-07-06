package com.ep.databuilder.log;

import com.ep.databuilder.common.PageResult;
import com.ep.databuilder.common.Result;
import com.ep.databuilder.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    private final OperationLogRepository logRepository;

    @GetMapping
    @RequireRole("ADMIN")
    public Result<PageResult<OperationLogEntity>> list(@RequestParam(required = false) String bizType,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"));
        Page<OperationLogEntity> result;
        if (bizType == null || bizType.trim().isEmpty()) {
            result = logRepository.findAll(pageable);
        } else {
            OperationLogEntity probe = new OperationLogEntity();
            probe.setBizType(bizType.trim());
            result = logRepository.findAll(Example.of(probe), pageable);
        }
        return Result.ok(PageResult.of(result.getTotalElements(), result.getContent()));
    }
}
