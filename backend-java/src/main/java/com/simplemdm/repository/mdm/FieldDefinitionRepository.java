package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.FieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, Long> {
    java.util.Optional<FieldDefinition> findBySystemIdAndId(Long systemId, Long id);
    boolean existsByObjectTypeIdAndFieldKey(Long objectTypeId, String fieldKey);
    java.util.List<FieldDefinition> findByObjectTypeId(Long objectTypeId);
    java.util.List<FieldDefinition> findBySystemIdAndObjectTypeIdIn(Long systemId, java.util.Collection<Long> objectTypeIds);
}
