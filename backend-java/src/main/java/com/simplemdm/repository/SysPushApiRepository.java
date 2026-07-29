package com.simplemdm.repository;

import com.simplemdm.model.SysPushApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SysPushApiRepository extends JpaRepository<SysPushApi, Long> {
    Optional<SysPushApi> findByTargetSystem(String targetSystem);
    List<SysPushApi> findByStatus(String status);
    Page<SysPushApi> findByNameContainingOrTargetSystemContaining(String nameKey, String sysKey, Pageable pageable);
}
