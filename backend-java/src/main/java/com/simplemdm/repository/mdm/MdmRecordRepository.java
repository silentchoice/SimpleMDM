package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.MdmRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MdmRecordRepository extends JpaRepository<MdmRecord, Long> { }
