package com.simplemdm.service.mdm;

import java.util.Map;

public record CreateRecordCommand(Long systemId, Long objectTypeId, Long departmentId, String recordCode,
                                  Map<String, Object> data) { }
