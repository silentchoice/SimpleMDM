package com.simplemdm.service.mdm;
import com.simplemdm.model.workflow.ApprovalRequest;import java.util.Map;
public interface ApprovedRecordWriter {RecordView apply(ApprovalRequest request,Long actorId,long expectedVersion,Map<String,Object> data);}