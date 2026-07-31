package com.simplemdm.service.integration;
import com.simplemdm.exception.BusinessException; import com.simplemdm.model.integration.*; import com.simplemdm.model.mdm.MdmRecord;
import com.simplemdm.repository.integration.*; import com.simplemdm.repository.mdm.MdmRecordRepository; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service("relationalPushService")
public class PushService {
 private final PushSubscriptionRepository subscriptions;private final PushLogRepository logs;private final MdmRecordRepository records;private final int snapshotLimit;
 public PushService(PushSubscriptionRepository subscriptions,PushLogRepository logs,MdmRecordRepository records,@Value("${simple-mdm.push.snapshot-limit:4096}") int snapshotLimit){this.subscriptions=subscriptions;this.logs=logs;this.records=records;this.snapshotLimit=Math.max(64,snapshotLimit);}
 @Transactional public void publishRecordChanged(Long recordId){
  MdmRecord r=records.findById(recordId).orElseThrow(()->new BusinessException(404,"Record not found"));
  String eventId="record:"+r.getId()+":version:"+r.getVersion();
  String snapshot="{\"record_id\":"+r.getId()+",\"system_id\":"+r.getSystemId()+",\"object_type_id\":"+r.getObjectTypeId()+",\"department_id\":"+r.getDepartmentId()+",\"record_code\":\""+escape(r.getRecordCode())+"\",\"version\":"+r.getVersion()+"}";
  if(snapshot.length()>snapshotLimit)snapshot=snapshot.substring(0,snapshotLimit);
  List<PushSubscription> matching=subscriptions.findActiveForEvent(r.getSystemId(),r.getObjectTypeId(),"RECORD_CHANGED");
  for(PushSubscription s:matching)logs.save(PushLog.pending(r.getSystemId(),s.getId(),r.getId(),eventId,snapshot));
 }
 private String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
}
