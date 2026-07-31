package com.simplemdm.repository.workflow;
import com.simplemdm.model.workflow.ApproverAssignment; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param;
public interface ApproverAssignmentRepository extends JpaRepository<ApproverAssignment,Long>{
 @Query("select (count(a)>0) from ApproverAssignment a where a.systemId=:s and (a.objectTypeId=:o or a.objectTypeId is null) and a.departmentId=:d and a.approverUserId=:u and a.status='active'")
 boolean existsActiveAssignment(@Param("s") Long systemId,@Param("o") Long objectTypeId,@Param("d") Long departmentId,@Param("u") Long userId);
}
