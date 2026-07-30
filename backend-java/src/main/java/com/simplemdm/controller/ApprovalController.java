package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.security.*;
import com.simplemdm.service.*;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final PermissionService permService;

    public ApprovalController(ApprovalService approvalService, PermissionService permService) {
        this.approvalService = approvalService;
        this.permService = permService;
    }

    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "all") String listType,
                            @RequestParam(defaultValue = "") String status,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int pageSize) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        List<String> approverDepts = permService.getViewableDepts(user.getId());
        Page<Map<String, Object>> result = approvalService.listApprovals(user.getId(), listType, status, page, pageSize, approverDepts);
        return ApiResponse.ok(new PageResult<>(result.getContent(), result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        Map<String, Object> detail = approvalService.getApprovalDetail(id);
        if (detail == null) return ApiResponse.error(404, "审批不存在");
        return ApiResponse.ok(detail);
    }

    @PostMapping("/{id}/approve")
    public ApiResponse approve(@PathVariable Long id, @RequestBody ApprovalDTO dto) {
        WfApproval a = approvalService.approve(id, dto.comment);
        if (a == null) return ApiResponse.error(400, "审批不存在或状态不是待审批");
        Map<String, Object> data = new HashMap<>();
        data.put("id", a.getId());
        data.put("status", a.getStatus());
        return ApiResponse.ok("审批已通过，数据已生效并推送至下游系统", data);
    }

    @PostMapping("/{id}/reject")
    public ApiResponse reject(@PathVariable Long id, @RequestBody ApprovalDTO dto) {
        WfApproval a = approvalService.reject(id, dto.comment);
        if (a == null) return ApiResponse.error(400, "审批不存在或状态不是待审批");
        Map<String, Object> data = new HashMap<>();
        data.put("id", a.getId());
        data.put("status", a.getStatus());
        return ApiResponse.ok("审批已驳回", data);
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse withdraw(@PathVariable Long id) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        WfApproval a = approvalService.withdraw(id, user.getId());
        if (a == null) return ApiResponse.error(400, "审批不存在、状态不是待审批、或非本人提交");
        Map<String, Object> data = new HashMap<>();
        data.put("id", a.getId());
        data.put("status", a.getStatus());
        return ApiResponse.ok("审批已撤回", data);
    }
}
