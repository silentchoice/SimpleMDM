package com.simplemdm.repository.mdm;
import com.simplemdm.model.mdm.ChildRecordValue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ChildRecordValueRepository extends JpaRepository<ChildRecordValue, Long> {
    java.util.List<ChildRecordValue> findByFieldDefinitionId(Long fieldDefinitionId);
    @Query("select value from ChildRecordValue value, ChildRecord child "
        + "where value.childRecordId = child.id and value.systemId = child.systemId "
        + "and value.fieldDefinitionId = :fieldDefinitionId and child.deletedAt is null "
        + "and child.status = 'active' order by value.id")
    java.util.List<ChildRecordValue> findActiveByFieldDefinitionId(
        @Param("fieldDefinitionId") Long fieldDefinitionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from ChildRecordValue value, ChildRecord child "
        + "where value.childRecordId = child.id and value.systemId = child.systemId "
        + "and value.fieldDefinitionId = :fieldDefinitionId and child.deletedAt is null order by value.id")
    java.util.List<ChildRecordValue> findActiveByFieldDefinitionIdForUpdate(
        @Param("fieldDefinitionId") Long fieldDefinitionId);
    java.util.Optional<ChildRecordValue> findByChildRecordIdAndFieldDefinitionId(Long childRecordId, Long fieldDefinitionId);
    java.util.List<ChildRecordValue> findByChildRecordIdIn(java.util.Collection<Long> childRecordIds);
    java.util.List<ChildRecordValue> findByChildRecordIdInAndFieldDefinitionIdIn(
        java.util.Collection<Long> childRecordIds, java.util.Collection<Long> fieldDefinitionIds);
    void deleteByChildRecordId(Long childRecordId);
}
