package com.ep.databuilder.factory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FactoryRepository extends JpaRepository<FactoryEntity, Long> {

    Optional<FactoryEntity> findByCodeAndDeletedFalse(String code);

    @Query("select f from FactoryEntity f where f.deleted = false"
            + " and (:keyword is null or f.code like %:keyword% or f.name like %:keyword%"
            + " or f.description like %:keyword%) order by f.updatedAt desc")
    Page<FactoryEntity> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("select f from FactoryEntity f where f.deleted = false and f.enabled = true"
            + " and (:keyword is null or f.code like %:keyword% or f.name like %:keyword%"
            + " or f.description like %:keyword%)")
    List<FactoryEntity> searchEnabled(@Param("keyword") String keyword, Pageable pageable);
}
