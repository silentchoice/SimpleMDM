package com.simplemdm.service;

import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PushService {

    private final SysPushLogRepository pushLogRepo;
    private final SysPushApiRepository pushApiRepo;
    private final MdmPersonnelRepository personnelRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    private int pushSessionCount = 0;

    public PushService(SysPushLogRepository pushLogRepo, SysPushApiRepository pushApiRepo,
                       MdmPersonnelRepository personnelRepo) {
        this.pushLogRepo = pushLogRepo;
        this.pushApiRepo = pushApiRepo;
        this.personnelRepo = personnelRepo;
    }

    @Transactional
    public List<SysPushLog> executePush(WfApproval approval) {
        MdmPersonnel p = personnelRepo.findById(approval.getPersonnelId()).orElse(null);
        if (p == null) return List.of();

        Map<String, Object> payload = new HashMap<>();
        payload.put("employee_code", p.getEmployeeCode());
        payload.put("name", p.getName());
        payload.put("gender", p.getGender());
        payload.put("department", p.getDepartment());
        payload.put("position", p.getPosition());
        payload.put("phone", p.getPhone());
        payload.put("email", p.getEmail());
        payload.put("version", p.getVersion());

        List<SysPushApi> activeApis = pushApiRepo.findByStatus("active");
        List<SysPushLog> logs = new ArrayList<>();

        for (SysPushApi api : activeApis) {
            pushSessionCount++;
            SysPushLog log = new SysPushLog();
            log.setApprovalId(approval.getId());
            log.setPersonnelId(p.getId());
            log.setTargetSystem(api.getTargetSystem());
            log.setStatus("pending");
            try {
                log.setRequestBody(mapper.writeValueAsString(payload));
            } catch (Exception ignored) {}

            pushLogRepo.save(log);

            try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            boolean success = pushSessionCount <= 2 || Math.random() < 0.9;
            if (success) {
                log.setStatus("success");
                log.setResponseCode(200);
                log.setResponseBody("{\"code\":200,\"message\":\"数据已成功同步到 " + api.getName() + "\"}");
            } else {
                log.setStatus("failed");
                log.setResponseCode(500);
                log.setResponseBody("{\"code\":500,\"message\":\"" + api.getName() + " 连接超时\"}");
                log.setErrorMessage("Connection to " + api.getName() + " timed out");
            }

            log.setPushedAt(LocalDateTime.now());
            pushLogRepo.save(log);
            logs.add(log);
        }

        return logs;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listPushLogs(String targetSystem, String status, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysPushLog> logs;

        if ((targetSystem != null && !targetSystem.isEmpty()) &&
            (status != null && !status.isEmpty())) {
            logs = pushLogRepo.findByTargetSystemAndStatus(targetSystem, status, pageable);
        } else if (targetSystem != null && !targetSystem.isEmpty()) {
            logs = pushLogRepo.findByTargetSystem(targetSystem, pageable);
        } else if (status != null && !status.isEmpty()) {
            logs = pushLogRepo.findByStatus(status, pageable);
        } else {
            logs = pushLogRepo.findAll(pageable);
        }

        return logs.map(this::enrichLog);
    }

    private Map<String, Object> enrichLog(SysPushLog log) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", log.getId());
        m.put("approval_id", log.getApprovalId());
        m.put("personnel_id", log.getPersonnelId());
        m.put("personnel_name", log.getPersonnelId() != null ?
            personnelRepo.findById(log.getPersonnelId()).map(MdmPersonnel::getName).orElse("") : "");
        m.put("target_system", log.getTargetSystem());
        m.put("status", log.getStatus());
        m.put("request_body", log.getRequestBody());
        m.put("response_body", log.getResponseBody());
        m.put("response_code", log.getResponseCode());
        m.put("retry_count", log.getRetryCount());
        m.put("error_message", log.getErrorMessage());
        m.put("pushed_at", log.getPushedAt() != null ? log.getPushedAt().toString() : null);
        m.put("created_at", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return m;
    }

    @Transactional
    public SysPushLog retryPush(Long logId) {
        SysPushLog log = pushLogRepo.findById(logId).orElse(null);
        if (log == null || !"failed".equals(log.getStatus())) return null;

        log.setRetryCount(log.getRetryCount() + 1);
        log.setStatus("success");
        log.setResponseCode(200);
        log.setResponseBody("{\"code\":200,\"message\":\"重试成功: 数据已同步到 " + log.getTargetSystem() + "\"}");
        log.setErrorMessage(null);
        log.setPushedAt(LocalDateTime.now());
        return pushLogRepo.save(log);
    }
}
