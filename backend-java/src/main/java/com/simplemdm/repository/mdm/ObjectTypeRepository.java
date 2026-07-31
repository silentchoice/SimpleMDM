package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.ObjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ObjectTypeRepository extends JpaRepository<ObjectType, Long> {
    Optional<ObjectType> findBySystemIdAndCode(Long systemId, String code);
}
