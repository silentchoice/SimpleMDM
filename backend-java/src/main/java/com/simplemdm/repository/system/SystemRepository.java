package com.simplemdm.repository.system;

import com.simplemdm.model.system.SystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemRepository extends JpaRepository<SystemEntity, Long> {
    Optional<SystemEntity> findByCode(String code);
}
