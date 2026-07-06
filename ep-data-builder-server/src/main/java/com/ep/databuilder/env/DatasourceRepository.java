package com.ep.databuilder.env;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DatasourceRepository extends JpaRepository<DatasourceEntity, Long> {

    List<DatasourceEntity> findByEnvIdOrderBySchemaCode(Long envId);

    Optional<DatasourceEntity> findByEnvIdAndSchemaCode(Long envId, String schemaCode);
}
