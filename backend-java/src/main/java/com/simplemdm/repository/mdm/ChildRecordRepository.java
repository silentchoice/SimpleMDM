package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.ChildRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChildRecordRepository extends JpaRepository<ChildRecord, Long> {
    Optional<ChildRecord> findByIdAndDeletedAtIsNull(Long id);
    @Query("select child from ChildRecord child where child.systemId = :systemId and child.recordId = :recordId "
        + "and child.childTypeId = :childTypeId and child.status = 'active' "
        + "and child.deletedAt is null order by child.id")
    List<ChildRecord> findBySystemIdAndRecordIdAndChildTypeId(@Param("systemId") Long systemId,
                                                              @Param("recordId") Long recordId,
                                                              @Param("childTypeId") Long childTypeId);

    List<ChildRecord> findBySystemIdAndRecordIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
        Long systemId, Long recordId);

    Optional<ChildRecord> findBySystemIdAndRecordIdAndChildTypeIdAndIdAndDeletedAtIsNull(
        Long systemId, Long recordId, Long childTypeId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select child from ChildRecord child where child.systemId = :systemId and child.id in :ids "
        + "and child.deletedAt is null order by child.id")
    List<ChildRecord> findAllBySystemIdAndIdInForUpdate(@Param("systemId") Long systemId,
                                                        @Param("ids") Collection<Long> ids);
}
