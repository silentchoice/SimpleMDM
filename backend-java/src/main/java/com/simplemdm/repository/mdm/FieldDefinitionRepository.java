package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, Long> {
    boolean existsByObjectTypeIdAndFieldKey(Long objectTypeId, String fieldKey);
}
