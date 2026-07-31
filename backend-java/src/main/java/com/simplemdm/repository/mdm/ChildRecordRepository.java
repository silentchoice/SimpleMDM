package com.simplemdm.repository.mdm;
import com.simplemdm.model.mdm.ChildRecord;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChildRecordRepository extends JpaRepository<ChildRecord, Long> {
    java.util.List<ChildRecord> findBySystemIdAndRecordIdAndChildTypeId(Long systemId, Long recordId, Long childTypeId);
}