package com.simplemdm.repository;

import com.simplemdm.model.MdmPersonnelSub;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MdmPersonnelSubRepository extends JpaRepository<MdmPersonnelSub, Long> {
    List<MdmPersonnelSub> findByPersonnelId(Long personnelId);
    List<MdmPersonnelSub> findByPersonnelIdAndVisibilityIn(Long personnelId, List<String> visibilities);
    List<MdmPersonnelSub> findByOwnerDeptAndSubType(String ownerDept, String subType);
}
