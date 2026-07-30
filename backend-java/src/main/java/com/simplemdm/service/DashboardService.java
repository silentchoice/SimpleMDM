package com.simplemdm.service;

import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardService {

    private final MdmPersonnelRepository personnelRepo;
    private final WfApprovalRepository approvalRepo;
    private final SysPushLogRepository pushLogRepo;
    private final SysUserRepository userRepo;
    private final PersonnelService personnelService;

    public DashboardService(MdmPersonnelRepository personnelRepo, WfApprovalRepository approvalRepo,
                            SysPushLogRepository pushLogRepo, SysUserRepository userRepo,
                            PersonnelService personnelService) {
        this.personnelRepo = personnelRepo;
        this.approvalRepo = approvalRepo;
        this.pushLogRepo = pushLogRepo;
        this.userRepo = userRepo;
        this.personnelService = personnelService;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalPersonnel = personnelRepo.count();
        long pendingApprovals = approvalRepo.countByStatus("pending");
        long totalPushes = pushLogRepo.countAll();
        long successPushes = pushLogRepo.countSuccess();
        double pushSuccessRate = totalPushes > 0 ? Math.round(successPushes * 1000.0 / totalPushes) / 10.0 : 100.0;

        stats.put("total_personnel", totalPersonnel);
        stats.put("pending_approvals", pendingApprovals);
        stats.put("push_success_rate", pushSuccessRate);

        // Recent 5 approvals
        List<WfApproval> recentApprovals = approvalRepo.findAll(
            org.springframework.data.domain.PageRequest.of(0, 5,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"))
        ).getContent();

        List<Map<String, Object>> recent = new ArrayList<>();
        for (WfApproval a : recentApprovals) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("personnel_name", personnelRepo.findById(a.getPersonnelId()).map(personnel -> {
                Object name = personnelService.readData(personnel).get("name");
                return name == null ? "#" + personnel.getId() : name.toString();
            }).orElse(""));
            item.put("workflow_type", a.getWorkflowType());
            item.put("submitter_name", userRepo.findById(a.getSubmitterId()).map(SysUser::getRealName).orElse(""));
            item.put("approver_name", a.getApproverId() != null ? userRepo.findById(a.getApproverId()).map(SysUser::getRealName).orElse("") : "");
            item.put("status", a.getStatus());
            item.put("submit_time", a.getSubmitTime() != null ? a.getSubmitTime().toString() : "");
            recent.add(item);
        }
        stats.put("recent_approvals", recent);

        return stats;
    }
}
