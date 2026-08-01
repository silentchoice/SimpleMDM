package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.RecordValue;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecordValueRepository extends JpaRepository<RecordValue, Long> {
    List<RecordValue> findByRecordId(Long recordId);
    List<RecordValue> findByRecordIdIn(java.util.Collection<Long> recordIds);
    List<RecordValue> findByFieldDefinitionId(Long fieldDefinitionId);
    @Query("select value from RecordValue value, MdmRecord record "
        + "where value.recordId = record.id and value.systemId = record.systemId "
        + "and value.fieldDefinitionId = :fieldDefinitionId and record.deletedAt is null")
    List<RecordValue> findActiveByFieldDefinitionId(@Param("fieldDefinitionId") Long fieldDefinitionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from RecordValue value, MdmRecord record "
        + "where value.recordId = record.id and value.systemId = record.systemId "
        + "and value.fieldDefinitionId = :fieldDefinitionId and record.deletedAt is null order by value.id")
    List<RecordValue> findActiveByFieldDefinitionIdForUpdate(@Param("fieldDefinitionId") Long fieldDefinitionId);
    Optional<RecordValue> findByRecordIdAndFieldDefinitionId(Long recordId, Long fieldDefinitionId);
}
