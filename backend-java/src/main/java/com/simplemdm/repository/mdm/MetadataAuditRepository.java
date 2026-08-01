package com.simplemdm.repository.mdm;

import com.simplemdm.model.mdm.MetadataAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataAuditRepository extends JpaRepository<MetadataAudit, Long> { }
