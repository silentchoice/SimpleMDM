package com.simplemdm.repository.mdm;
import com.simplemdm.model.mdm.MdmRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
public interface MdmRecordRepository extends JpaRepository<MdmRecord, Long> {
    Optional<MdmRecord> findBySystemIdAndId(Long systemId, Long id);
    List<MdmRecord> findBySystemIdAndIdIn(Long systemId, Collection<Long> ids);
    Optional<MdmRecord> findBySystemIdAndObjectTypeIdAndIdAndDeletedAtIsNull(
        Long systemId, Long objectTypeId, Long id);
    List<MdmRecord> findBySystemIdAndObjectTypeIdAndDepartmentIdIn(Long systemId, Long objectTypeId, Collection<Long> departmentIds);
    List<MdmRecord> findBySystemIdAndObjectTypeIdAndStatusAndDeletedAtIsNullOrderById(
        Long systemId, Long objectTypeId, String status);
    boolean existsBySystemIdAndObjectTypeIdAndRecordCodeAndDeletedAtIsNull(Long systemId, Long objectTypeId, String recordCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from MdmRecord record where record.systemId = :systemId "
        + "and record.objectTypeId = :objectTypeId and record.recordCode = :recordCode "
        + "and record.deletedAt is null")
    List<MdmRecord> findByRecordCodeForUpdate(@Param("systemId") Long systemId,
                                               @Param("objectTypeId") Long objectTypeId,
                                               @Param("recordCode") String recordCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from MdmRecord record where record.systemId = :systemId and record.id = :id")
    Optional<MdmRecord> findBySystemIdAndIdForUpdate(@Param("systemId") Long systemId, @Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from MdmRecord record where record.systemId = :systemId "
        + "and record.objectTypeId = :objectTypeId and record.departmentId = :departmentId "
        + "and record.id = :id and record.deletedAt is null")
    Optional<MdmRecord> findApprovalTargetForUpdate(@Param("systemId") Long systemId,
                                                     @Param("objectTypeId") Long objectTypeId,
                                                     @Param("departmentId") Long departmentId,
                                                     @Param("id") Long id);
}
