package com.ep.databuilder.buildrec;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildRepository extends JpaRepository<BuildEntity, Long> {

    @Query("select b from BuildEntity b where (:factoryId is null or b.factoryId = :factoryId)"
            + " and (:envId is null or b.envId = :envId)"
            + " and (:status is null or b.status = :status)"
            + " order by b.id desc")
    Page<BuildEntity> search(@Param("factoryId") Long factoryId,
                             @Param("envId") Long envId,
                             @Param("status") String status,
                             Pageable pageable);
}
