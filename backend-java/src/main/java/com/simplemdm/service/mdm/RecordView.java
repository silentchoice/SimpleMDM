package com.simplemdm.service.mdm;

public record RecordView(Long id, Long systemId, Long objectTypeId, Long departmentId, String recordCode,
                         long version) { }
