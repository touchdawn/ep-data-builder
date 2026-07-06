package com.ep.databuilder.env;

import com.ep.databuilder.common.Result;
import com.ep.databuilder.env.EnvDTOs.DatasourceSaveDTO;
import com.ep.databuilder.env.EnvDTOs.DatasourceVO;
import com.ep.databuilder.env.EnvDTOs.EndpointSaveDTO;
import com.ep.databuilder.env.EnvDTOs.EnvSaveDTO;
import com.ep.databuilder.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EnvController {

    private final EnvService envService;

    @GetMapping("/environments")
    public Result<List<EnvironmentEntity>> listEnvs() {
        return Result.ok(envService.listEnvs());
    }

    @PostMapping("/environments")
    @RequireRole("ADMIN")
    public Result<Long> createEnv(@Valid @RequestBody EnvSaveDTO dto) {
        return Result.ok(envService.createEnv(dto));
    }

    @PutMapping("/environments/{id}")
    @RequireRole("ADMIN")
    public Result<Void> updateEnv(@PathVariable Long id, @Valid @RequestBody EnvSaveDTO dto) {
        envService.updateEnv(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/environments/{id}")
    @RequireRole("ADMIN")
    public Result<Void> deleteEnv(@PathVariable Long id) {
        envService.deleteEnv(id);
        return Result.ok();
    }

    @GetMapping("/environments/{envId}/endpoints")
    public Result<List<ModuleEndpointEntity>> listEndpoints(@PathVariable Long envId) {
        return Result.ok(envService.listEndpoints(envId));
    }

    @PostMapping("/environments/{envId}/endpoints")
    @RequireRole("ADMIN")
    public Result<Long> createEndpoint(@PathVariable Long envId, @Valid @RequestBody EndpointSaveDTO dto) {
        return Result.ok(envService.createEndpoint(envId, dto));
    }

    @PutMapping("/endpoints/{id}")
    @RequireRole("ADMIN")
    public Result<Void> updateEndpoint(@PathVariable Long id, @Valid @RequestBody EndpointSaveDTO dto) {
        envService.updateEndpoint(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/endpoints/{id}")
    @RequireRole("ADMIN")
    public Result<Void> deleteEndpoint(@PathVariable Long id) {
        envService.deleteEndpoint(id);
        return Result.ok();
    }

    @GetMapping("/environments/{envId}/datasources")
    public Result<List<DatasourceVO>> listDatasources(@PathVariable Long envId) {
        return Result.ok(envService.listDatasources(envId).stream()
                .map(DatasourceVO::of)
                .collect(Collectors.toList()));
    }

    @PostMapping("/environments/{envId}/datasources")
    @RequireRole("ADMIN")
    public Result<Long> createDatasource(@PathVariable Long envId, @Valid @RequestBody DatasourceSaveDTO dto) {
        return Result.ok(envService.createDatasource(envId, dto));
    }

    @PutMapping("/datasources/{id}")
    @RequireRole("ADMIN")
    public Result<Void> updateDatasource(@PathVariable Long id, @Valid @RequestBody DatasourceSaveDTO dto) {
        envService.updateDatasource(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/datasources/{id}")
    @RequireRole("ADMIN")
    public Result<Void> deleteDatasource(@PathVariable Long id) {
        envService.deleteDatasource(id);
        return Result.ok();
    }

    @PostMapping("/datasources/{id}/test-connection")
    @RequireRole("ADMIN")
    public Result<Void> testConnection(@PathVariable Long id) {
        envService.testConnection(id);
        return Result.ok();
    }
}
