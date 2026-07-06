package com.ep.databuilder.buildrec;

import com.ep.databuilder.buildrec.BuildDTOs.BuildDetailVO;
import com.ep.databuilder.buildrec.BuildDTOs.BuildListVO;
import com.ep.databuilder.buildrec.BuildDTOs.StepLogVO;
import com.ep.databuilder.common.BizException;
import com.ep.databuilder.common.PageResult;
import com.ep.databuilder.env.EnvironmentRepository;
import com.ep.databuilder.factory.FactoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BuildQueryService {

    private final BuildRepository buildRepository;
    private final BuildStepLogRepository stepLogRepository;
    private final FactoryRepository factoryRepository;
    private final EnvironmentRepository environmentRepository;

    public PageResult<BuildListVO> search(Long factoryId, Long envId, String status, int page, int size) {
        String st = (status == null || status.trim().isEmpty()) ? null : status.trim();
        Page<BuildEntity> result = buildRepository.search(factoryId, envId, st,
                PageRequest.of(Math.max(page - 1, 0), size));
        List<BuildListVO> list = result.getContent().stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(result.getTotalElements(), list);
    }

    public BuildDetailVO detail(Long id) {
        BuildEntity build = buildRepository.findById(id)
                .orElseThrow(() -> new BizException("执行记录不存在：id=" + id));
        return toDetailVO(build);
    }

    public BuildDetailVO toDetailVO(BuildEntity build) {
        BuildDetailVO vo = new BuildDetailVO();
        vo.setId(build.getId());
        fillNames(vo, build);
        vo.setSource(build.getSource());
        vo.setTokenName(build.getTokenName());
        vo.setStatus(build.getStatus());
        vo.setParamsJson(build.getParamsJson());
        vo.setOutputsJson(build.getOutputsJson());
        vo.setErrorMsg(build.getErrorMsg());
        vo.setDurationMs(build.getDurationMs());
        vo.setCreatedBy(build.getCreatedBy());
        vo.setCreatedAt(build.getCreatedAt());
        for (BuildStepLogEntity step : stepLogRepository.findByBuildIdOrderBySortNo(build.getId())) {
            StepLogVO stepVO = new StepLogVO();
            stepVO.setSortNo(step.getSortNo());
            stepVO.setStepType(step.getStepType());
            stepVO.setStepName(step.getStepName());
            stepVO.setSkipped(step.getSkipped());
            stepVO.setRequest(step.getRequest());
            stepVO.setResponse(step.getResponse());
            stepVO.setOutputsJson(step.getOutputsJson());
            stepVO.setStatus(step.getStatus());
            stepVO.setErrorMsg(step.getErrorMsg());
            stepVO.setDurationMs(step.getDurationMs());
            vo.getSteps().add(stepVO);
        }
        return vo;
    }

    private BuildListVO toListVO(BuildEntity build) {
        BuildListVO vo = new BuildListVO();
        vo.setId(build.getId());
        BuildDetailVO tmp = new BuildDetailVO();
        fillNames(tmp, build);
        vo.setFactoryCode(tmp.getFactoryCode());
        vo.setFactoryName(tmp.getFactoryName());
        vo.setEnvCode(tmp.getEnvCode());
        vo.setSource(build.getSource());
        vo.setTokenName(build.getTokenName());
        vo.setStatus(build.getStatus());
        vo.setErrorMsg(build.getErrorMsg());
        vo.setDurationMs(build.getDurationMs());
        vo.setCreatedBy(build.getCreatedBy());
        vo.setCreatedAt(build.getCreatedAt());
        return vo;
    }

    private void fillNames(BuildDetailVO vo, BuildEntity build) {
        factoryRepository.findById(build.getFactoryId()).ifPresent(f -> {
            vo.setFactoryCode(f.getCode());
            vo.setFactoryName(f.getName());
        });
        environmentRepository.findById(build.getEnvId()).ifPresent(e -> vo.setEnvCode(e.getCode()));
    }
}
