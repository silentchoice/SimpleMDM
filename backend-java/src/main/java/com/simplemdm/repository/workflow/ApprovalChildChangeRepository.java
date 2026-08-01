package com.simplemdm.repository.workflow;

import com.simplemdm.model.workflow.ApprovalChildChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalChildChangeRepository extends JpaRepository<ApprovalChildChange, Long> {
    List<ApprovalChildChange> findByApprovalRequestIdOrderBySortOrderAscIdAsc(Long approvalRequestId);
}
