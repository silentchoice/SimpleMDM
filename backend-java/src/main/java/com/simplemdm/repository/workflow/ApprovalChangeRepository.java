package com.simplemdm.repository.workflow; import com.simplemdm.model.workflow.ApprovalChange; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface ApprovalChangeRepository extends JpaRepository<ApprovalChange,Long>{List<ApprovalChange> findByApprovalRequestId(Long id);}
