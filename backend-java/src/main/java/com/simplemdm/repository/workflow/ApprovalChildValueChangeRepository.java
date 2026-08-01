package com.simplemdm.repository.workflow;

import com.simplemdm.model.workflow.ApprovalChildValueChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ApprovalChildValueChangeRepository extends JpaRepository<ApprovalChildValueChange, Long> {
    List<ApprovalChildValueChange> findByApprovalChildChangeId(Long approvalChildChangeId);
    List<ApprovalChildValueChange> findByApprovalChildChangeIdIn(Collection<Long> approvalChildChangeIds);
    List<ApprovalChildValueChange> findByApprovalChildChangeIdInAndFieldDefinitionIdIn(
        Collection<Long> approvalChildChangeIds, Collection<Long> fieldDefinitionIds);
}
