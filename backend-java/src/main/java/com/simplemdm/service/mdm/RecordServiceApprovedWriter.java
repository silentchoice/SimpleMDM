package com.simplemdm.service.mdm;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
final class RecordServiceApprovedWriter implements ApprovedRecordWriter {
    private final RecordService records;
    RecordServiceApprovedWriter(RecordService records) { this.records = records; }
    public RecordView apply(Long actorId, Long recordId, long expectedVersion, Map<String, Object> data) {
        return records.applyApprovedUpdate(actorId, recordId, expectedVersion, data);
    }
}
