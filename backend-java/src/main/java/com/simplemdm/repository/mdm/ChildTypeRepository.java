package com.simplemdm.repository.mdm;
import com.simplemdm.model.mdm.ChildType;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChildTypeRepository extends JpaRepository<ChildType, Long> {
    java.util.Optional<ChildType> findBySystemIdAndId(Long systemId, Long id);
    java.util.Optional<ChildType> findBySystemIdAndObjectTypeIdAndCode(Long systemId, Long objectTypeId, String code);
    java.util.List<ChildType> findBySystemIdAndObjectTypeIdInAndStatusOrderBySortOrderAscIdAsc(
        Long systemId, java.util.Collection<Long> objectTypeIds, String status);
    java.util.List<ChildType> findBySystemIdAndObjectTypeIdInOrderBySortOrderAscIdAsc(
        Long systemId, java.util.Collection<Long> objectTypeIds);
}
