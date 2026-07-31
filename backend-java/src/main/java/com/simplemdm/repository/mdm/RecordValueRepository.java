package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.RecordValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecordValueRepository extends JpaRepository<RecordValue, Long> {
    List<RecordValue> findByRecordId(Long recordId);
    List<RecordValue> findByRecordIdIn(java.util.Collection<Long> recordIds);
    List<RecordValue> findByFieldDefinitionId(Long fieldDefinitionId);
    Optional<RecordValue> findByRecordIdAndFieldDefinitionId(Long recordId, Long fieldDefinitionId);
}
