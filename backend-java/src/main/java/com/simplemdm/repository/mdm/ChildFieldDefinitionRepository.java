package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.ChildFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildFieldDefinitionRepository extends JpaRepository<ChildFieldDefinition, Long> {
    java.util.Optional<ChildFieldDefinition> findBySystemIdAndId(Long systemId, Long id);
    boolean existsByChildTypeIdAndFieldKey(Long childTypeId, String fieldKey);
    java.util.List<ChildFieldDefinition> findByChildTypeId(Long childTypeId);
    java.util.List<ChildFieldDefinition> findByChildTypeIdAndSharedTrue(Long childTypeId);
    java.util.List<ChildFieldDefinition> findByChildTypeIdAndSharedTrueAndStatusOrderBySortOrderAscIdAsc(
        Long childTypeId, String status);
    java.util.List<ChildFieldDefinition> findBySystemIdAndChildTypeIdInAndStatusOrderBySortOrderAscIdAsc(
        Long systemId, java.util.Collection<Long> childTypeIds, String status);
    java.util.List<ChildFieldDefinition> findBySystemIdAndChildTypeIdInOrderBySortOrderAscIdAsc(
        Long systemId, java.util.Collection<Long> childTypeIds);
}
