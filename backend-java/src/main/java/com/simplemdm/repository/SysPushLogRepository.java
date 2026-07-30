package com.simplemdm.repository;

import com.simplemdm.model.SysPushLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SysPushLogRepository extends JpaRepository<SysPushLog, Long> {
    Page<SysPushLog> findByTargetSystem(String targetSystem, Pageable pageable);
    Page<SysPushLog> findByStatus(String status, Pageable pageable);
    Page<SysPushLog> findByTargetSystemAndStatus(String targetSystem, String status, Pageable pageable);

    @Query("SELECT COUNT(l) FROM SysPushLog l")
    long countAll();

    @Query("SELECT COUNT(l) FROM SysPushLog l WHERE l.status = 'success'")
    long countSuccess();

    long countByStatus(String status);
}
