package com.simplemdm.service;

import com.simplemdm.dto.PersonnelDTO;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ApprovalService {

    private final WfApprovalRepository approvalRepo;
    private final MdmPersonnelRepository personnelRepo;
    private final SysUserRepository userRepo;
    private final SysApproverDeptRepository approverDeptRepo;
    private final PersonnelService personnelService;
    private final PushService pushService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApprovalService(WfApprovalRepository approvalRepo, MdmPersonnelRepository personnelRepo,
                           SysUserRepository userRepo, SysApproverDeptRepository approverDeptRepo,
                           PersonnelService personnelService, PushService pushService) {
        this.approvalRepo = approvalRepo;
        this.personnelRepo = personnelRepo;
        this.userRepo = userRepo;
        this.approverDeptRepo = approverDeptRepo;
        this.personnelService = personnelService;
        this.pushService = pushService;
    }

    @Transactional
    public WfApproval createApprovalForCreate(Long submitterId, PersonnelDTO dto) {
        MdmPersonnel p = personnelService.createFromApproval(dto);

        Map<String, Map<String, Object>> changeData = new HashMap<>();
        for (java.lang.reflect.Field f : PersonnelDTO.class.getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object val = f.get(dto);
                if (val != null && !"id".equals(f.getName()) && !"version".equals(f.getName()) && !"status".equals(f.getName())) {
                    changeData.put(f.getName(), Map.of("old", null, "new", val));
                }
            } catch (Exception ignored) {}
        }

        Long approverId = findApproverForDepartment(p.getDepartment());

        WfApproval approval = new WfApproval();
        approval.setPersonnelId(p.getId());
        approval.setWorkflowType("create");
        approval.setSubmitterId(submitterId);
        approval.setApproverId(approverId);
        approval.setStatus("pending");
        try {
            approval.setChangeData(mapper.writeValueAsString(changeData));
        } catch (Exception e) { throw new RuntimeException(e); }
        return approvalRepo.save(approval);
    }

    @Transactional
    public WfApproval createApprovalForUpdate(Long personnelId, Long submitterId, PersonnelDTO dto) {
        MdmPersonnel p = personnelRepo.findById(personnelId).orElse(null);
        if (p == null || "pending_approval".equals(p.getStatus())) return null;

        Map<String, Object> diffResult = personnelService.computeDiff(p, dto);
        if (diffResult == null) return null;

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> diff = (Map<String, Map<String, Object>>) diffResult.get("diff");
        if (diff.isEmpty()) return null;

        p.setStatus("pending_approval");
        personnelRepo.save(p);

        Long approverId = findApproverForDepartment(p.getDepartment());

        WfApproval approval = new WfApproval();
        approval.setPersonnelId(personnelId);
        approval.setWorkflowType("update");
        approval.setSubmitterId(submitterId);
        approval.setApproverId(approverId);
        approval.setStatus("pending");
        try {
            approval.setChangeData(mapper.writeValueAsString(diff));
        } catch (Exception e) { throw new RuntimeException(e); }
        return approvalRepo.save(approval);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listApprovals(Long userId, String listType, String statusFilter,
                                                    int page, int pageSize, List<String> approverDepts) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<WfApproval> approvals;

        if ("pending_my".equals(listType) && approverDepts != null) {
            // Find all approvers in the user's managed departments
            List<Long> approverIds = new ArrayList<>();
            approverIds.add(userId);
            approvals = approvalRepo.findByApproverIdInAndStatus(approverIds, "pending", pageable);
        } else if ("pending_my".equals(listType)) {
            approvals = approvalRepo.findByApproverIdAndStatus(userId, "pending", pageable);
        } else if ("my_submitted".equals(listType)) {
            approvals = approvalRepo.findBySubmitterId(userId, pageable);
        } else {
            approvals = approvalRepo.findAll(pageable);
        }

        return approvals.map(this::enrichApproval);
    }

    private Map<String, Object> enrichApproval(WfApproval a) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", a.getId());
        result.put("personnel_id", a.getPersonnelId());
        result.put("personnel_name", personnelRepo.findById(a.getPersonnelId()).map(MdmPersonnel::getName).orElse(""));
        result.put("workflow_type", a.getWorkflowType());
        result.put("submitter_id", a.getSubmitterId());
        result.put("submitter_name", userRepo.findById(a.getSubmitterId()).map(SysUser::getRealName).orElse(""));
        result.put("approver_id", a.getApproverId());
        result.put("approver_name", a.getApproverId() != null ? userRepo.findById(a.getApproverId()).map(SysUser::getRealName).orElse("") : "");
        result.put("status", a.getStatus());
        result.put("change_data", a.getChangeData());
        result.put("submit_time", a.getSubmitTime() != null ? a.getSubmitTime().toString() : null);
        result.put("approve_time", a.getApproveTime() != null ? a.getApproveTime().toString() : null);
        result.put("approve_comment", a.getApproveComment());
        result.put("withdrawn_time", a.getWithdrawnTime() != null ? a.getWithdrawnTime().toString() : null);
        result.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        return result;
    }

    @Transactional
    public WfApproval approve(Long approvalId, String comment) {
        WfApproval a = approvalRepo.findById(approvalId).orElse(null);
        if (a == null || !"pending".equals(a.getStatus())) return null;

        a.setStatus("approved");
        a.setApproveTime(LocalDateTime.now());
        a.setApproveComment(comment);

        MdmPersonnel p = personnelRepo.findById(a.getPersonnelId()).orElse(null);
        if (p != null && a.getChangeData() != null) {
            personnelService.applyChanges(p, a.getChangeData());
        }

        approvalRepo.save(a);

        // Trigger push
        pushService.executePush(a);

        return a;
    }

    @Transactional
    public WfApproval reject(Long approvalId, String comment) {
        WfApproval a = approvalRepo.findById(approvalId).orElse(null);
        if (a == null || !"pending".equals(a.getStatus())) return null;

        a.setStatus("rejected");
        a.setApproveTime(LocalDateTime.now());
        a.setApproveComment(comment);

        MdmPersonnel p = personnelRepo.findById(a.getPersonnelId()).orElse(null);
        if (p != null) {
            if ("create".equals(a.getWorkflowType())) {
                p.setStatus("inactive");
            } else {
                p.setStatus("active");
            }
            personnelRepo.save(p);
        }

        return approvalRepo.save(a);
    }

    @Transactional
    public WfApproval withdraw(Long approvalId, Long userId) {
        WfApproval a = approvalRepo.findById(approvalId).orElse(null);
        if (a == null || !"pending".equals(a.getStatus()) || !a.getSubmitterId().equals(userId)) return null;

        a.setStatus("withdrawn");
        a.setWithdrawnTime(LocalDateTime.now());

        MdmPersonnel p = personnelRepo.findById(a.getPersonnelId()).orElse(null);
        if (p != null) {
            if ("create".equals(a.getWorkflowType())) {
                p.setStatus("inactive");
            } else {
                p.setStatus("active");
            }
            personnelRepo.save(p);
        }

        return approvalRepo.save(a);
    }

    public Map<String, Object> getApprovalDetail(Long id) {
        WfApproval a = approvalRepo.findById(id).orElse(null);
        if (a == null) return null;
        return enrichApproval(a);
    }

    private Long findApproverForDepartment(String department) {
        // Find approver assigned to this department
        List<SysApproverDept> assignments = approverDeptRepo.findByDepartment(department);
        if (!assignments.isEmpty()) {
            return assignments.get(0).getUserId();
        }
        // Fallback: first active admin
        List<SysUser> users = userRepo.findAll();
        for (SysUser u : users) {
            if ("active".equals(u.getStatus()) && Boolean.TRUE.equals(u.getIsAdmin())) {
                return u.getId();
            }
        }
        return null;
    }
}
