package com.simplemdm.repository.mdm;
import com.simplemdm.model.mdm.MdmRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
public interface MdmRecordRepository extends JpaRepository<MdmRecord, Long> {
    Optional<MdmRecord> findBySystemIdAndId(Long systemId, Long id);
    List<MdmRecord> findBySystemIdAndObjectTypeIdAndDepartmentIdIn(Long systemId, Long objectTypeId, Collection<Long> departmentIds);
}