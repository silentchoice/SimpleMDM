package com.simplemdm.service.mdm;

public record ChildRecordView(Long id, Long parentRecordId, Long childTypeId, Long systemId, Long departmentId,
                              long version) { }
