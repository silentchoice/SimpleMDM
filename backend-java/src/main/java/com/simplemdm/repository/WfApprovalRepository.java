package com.simplemdm.repository;

import com.simplemdm.model.WfApproval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WfApprovalRepository extends JpaRepository<WfApproval, Long> {
    Page<WfApproval> findByApproverIdAndStatus(Long approverId, String status, Pageable pageable);
    Page<WfApproval> findBySubmitterId(Long submitterId, Pageable pageable);
    Page<WfApproval> findByApproverIdInAndStatus(List<Long> approverIds, String status, Pageable pageable);
    long countByStatus(String status);
}
