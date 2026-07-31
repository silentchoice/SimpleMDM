package com.simplemdm.repository.mdm;
import com.simplemdm.model.mdm.ChildType;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChildTypeRepository extends JpaRepository<ChildType, Long> {
    java.util.Optional<ChildType> findBySystemIdAndObjectTypeIdAndCode(Long systemId, Long objectTypeId, String code);
}