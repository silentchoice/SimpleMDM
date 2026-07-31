package com.simplemdm.service.mdm;

import java.util.Map;

public record UpdateRecordCommand(Long systemId, Long objectTypeId, Long recordId, Long departmentId,
                                  long expectedVersion, Map<String, Object> data) { }
