package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.ObjectType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ObjectTypeRepository extends JpaRepository<ObjectType, Long> {
    Optional<ObjectType> findBySystemIdAndCode(Long systemId, String code);
    java.util.List<ObjectType> findBySystemId(Long systemId);
    Optional<ObjectType> findBySystemIdAndId(Long systemId, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select objectType from ObjectType objectType where objectType.systemId = :systemId and objectType.id = :id")
    Optional<ObjectType> findBySystemIdAndIdForUpdate(@Param("systemId") Long systemId, @Param("id") Long id);
}
