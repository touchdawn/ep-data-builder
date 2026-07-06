package com.ep.databuilder.env;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModuleEndpointRepository extends JpaRepository<ModuleEndpointEntity, Long> {

    List<ModuleEndpointEntity> findByEnvIdOrderByModuleCode(Long envId);

    Optional<ModuleEndpointEntity> findByEnvIdAndModuleCode(Long envId, String moduleCode);
}
