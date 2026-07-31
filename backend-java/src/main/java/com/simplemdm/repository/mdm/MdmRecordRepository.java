package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.MdmRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MdmRecordRepository extends JpaRepository<MdmRecord, Long> {
    List<MdmRecord> findBySystemIdAndObjectTypeIdAndDepartmentIdIn(Long systemId, Long objectTypeId,
                                                                     Collection<Long> departmentIds);
}
