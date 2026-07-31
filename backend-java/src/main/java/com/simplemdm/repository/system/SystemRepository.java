package com.simplemdm.repository.system;

import com.simplemdm.model.system.SystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface SystemRepository extends JpaRepository<SystemEntity, Long> {
    Optional<SystemEntity> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SystemEntity> findForUpdateByCode(String code);
}
