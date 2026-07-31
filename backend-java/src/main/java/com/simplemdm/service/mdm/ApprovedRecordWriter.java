package com.simplemdm.service.mdm;

import java.util.Map;

public interface ApprovedRecordWriter {
    RecordView apply(Long actorId, Long recordId, long expectedVersion, Map<String, Object> data);
}
