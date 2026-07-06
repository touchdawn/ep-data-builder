package com.ep.databuilder.buildrec;

import com.ep.databuilder.buildrec.BuildDTOs.BuildDetailVO;
import com.ep.databuilder.buildrec.BuildDTOs.BuildListVO;
import com.ep.databuilder.buildrec.BuildDTOs.ConsoleBuildRequest;
import com.ep.databuilder.common.PageResult;
import com.ep.databuilder.common.Result;
import com.ep.databuilder.engine.BuildService;
import com.ep.databuilder.env.EnvService;
import com.ep.databuilder.factory.FactoryService;
import com.ep.databuilder.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/builds")
@RequiredArgsConstructor
public class BuildController {

    private final BuildService buildService;
    private final BuildQueryService buildQueryService;
    private final FactoryService factoryService;
    private final EnvService envService;

    /** 控制台造数：同步执行并返回完整轨迹 */
    @PostMapping
    @RequireRole("EDITOR")
    public Result<BuildDetailVO> build(@Valid @RequestBody ConsoleBuildRequest request) {
        BuildEntity build = buildService.execute(
                factoryService.getFactory(request.getFactoryId()),
                envService.getEnv(request.getEnvId()),
                request.getParams(), "CONSOLE", null);
        return Result.ok(buildQueryService.toDetailVO(build));
    }

    @GetMapping
    public Result<PageResult<BuildListVO>> list(@RequestParam(required = false) Long factoryId,
                                                @RequestParam(required = false) Long envId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.ok(buildQueryService.search(factoryId, envId, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<BuildDetailVO> detail(@PathVariable Long id) {
        return Result.ok(buildQueryService.detail(id));
    }
}
