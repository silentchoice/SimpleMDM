package com.simplemdm.repository.workflow;

import com.simplemdm.model.workflow.ApprovalRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findBySystemIdOrderByIdDesc(Long systemId);
    Optional<ApprovalRequest> findBySystemIdAndId(Long systemId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ApprovalRequest request where request.systemId = :systemId and request.id = :id")
    Optional<ApprovalRequest> findBySystemIdAndIdForUpdate(@Param("systemId") Long systemId, @Param("id") Long id);
}
