package com.simplemdm.service.mdm;

import java.util.Map;

public record CreateChildRecordCommand(Long parentRecordId, Long childTypeId, Map<String, Object> data) { }
