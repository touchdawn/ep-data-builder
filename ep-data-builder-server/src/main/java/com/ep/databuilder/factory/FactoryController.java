package com.ep.databuilder.factory;

import com.ep.databuilder.common.PageResult;
import com.ep.databuilder.common.Result;
import com.ep.databuilder.factory.FactoryDTOs.FactoryCreateDTO;
import com.ep.databuilder.factory.FactoryDTOs.FactoryDetailDTO;
import com.ep.databuilder.factory.FactoryDTOs.FactoryListVO;
import com.ep.databuilder.factory.FactoryDTOs.FactorySaveDTO;
import com.ep.databuilder.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/factories")
@RequiredArgsConstructor
public class FactoryController {

    private final FactoryService factoryService;

    @GetMapping
    public Result<PageResult<FactoryListVO>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(factoryService.list(keyword, page, size));
    }

    @PostMapping
    @RequireRole("EDITOR")
    public Result<Long> create(@Valid @RequestBody FactoryCreateDTO dto) {
        return Result.ok(factoryService.create(dto));
    }

    @GetMapping("/{id}")
    public Result<FactoryDetailDTO> detail(@PathVariable Long id) {
        return Result.ok(factoryService.detail(id));
    }

    @PutMapping("/{id}")
    @RequireRole("EDITOR")
    public Result<Void> save(@PathVariable Long id, @Valid @RequestBody FactorySaveDTO dto) {
        factoryService.save(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole("EDITOR")
    public Result<Void> delete(@PathVariable Long id) {
        factoryService.delete(id);
        return Result.ok();
    }
}
