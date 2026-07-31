package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.ChildFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildFieldDefinitionRepository extends JpaRepository<ChildFieldDefinition, Long> {
    java.util.List<ChildFieldDefinition> findByChildTypeId(Long childTypeId);
}
