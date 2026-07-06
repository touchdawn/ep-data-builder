package com.ep.databuilder.open;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OpenTokenRepository extends JpaRepository<OpenTokenEntity, Long> {

    Optional<OpenTokenEntity> findByTokenHashAndEnabledTrue(String tokenHash);
}
