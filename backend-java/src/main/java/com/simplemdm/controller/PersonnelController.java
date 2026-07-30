package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.security.*;
import com.simplemdm.service.*;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {

    private final PersonnelService personnelService;
    private final ApprovalService approvalService;
    private final PermissionService permService;

    public PersonnelController(PersonnelService personnelService, ApprovalService approvalService,
                               PermissionService permService) {
        this.personnelService = personnelService;
        this.approvalService = approvalService;
        this.permService = permService;
    }

    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "") String department,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int pageSize) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        List<String> allowedDepts = permService.getViewableDepts(user.getId());
        List<String> allowedSystems = permService.getPermittedSystems(user.getId(), "VIEW");
        String systemCode = (allowedSystems == null || allowedSystems.isEmpty()) ? null : allowedSystems.get(0);
        Page<MdmPersonnel> result = personnelService.listPersonnel(keyword, department, page, pageSize, allowedDepts, systemCode);

        List<Map<String, Object>> items = result.getContent().stream()
            .map(personnelService::toMap)
            .collect(Collectors.toList());

        return ApiResponse.ok(new PageResult<>(items, result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/departments")
    public ApiResponse departments() {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        return ApiResponse.ok(personnelService.getDepartments(currentSystem(user)));
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        MdmPersonnel p = personnelService.getPersonnel(id);
        if (p == null) return ApiResponse.error(404, "人员不存在");
        return ApiResponse.ok(personnelService.toMap(p));
    }

    @PostMapping
    @RequirePerm("EDIT")
    public ApiResponse create(@Valid @RequestBody DynamicPersonnelDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        if (!canEditDepartment(user, dto.ownerDept)) {
            return ApiResponse.error(403, "无权编辑部门: " + dto.ownerDept);
        }
        String systemCode = currentSystem(user);
        WfApproval approval = approvalService.createApprovalForCreate(user.getId(), dto, systemCode);
        Map<String, Object> data = new HashMap<>();
        data.put("personnel_id", approval.getPersonnelId());
        data.put("approval_id", approval.getId());
        return ApiResponse.ok("提交成功，请等待审批", data);
    }

    @PutMapping("/{id}")
    @RequirePerm("EDIT")
    public ApiResponse update(@PathVariable Long id, @Valid @RequestBody DynamicPersonnelDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        MdmPersonnel existing = personnelService.getPersonnel(id);
        if (existing == null) return ApiResponse.error(404, "人员不存在");
        if (!canEditDepartment(user, existing.getOwnerDept()) || !canEditDepartment(user, dto.ownerDept)) {
            return ApiResponse.error(403, "无权编辑该部门主数据");
        }
        WfApproval approval = approvalService.createApprovalForUpdate(id, user.getId(), dto);
        if (approval == null) return ApiResponse.ok("没有变更需要提交", null);
        Map<String, Object> data = new HashMap<>();
        data.put("personnel_id", id);
        data.put("approval_id", approval.getId());
        return ApiResponse.ok("变更已提交，请等待审批", data);
    }

    private String currentSystem(SysUser user) {
        List<String> systems = permService.getPermittedSystems(user.getId(), "EDIT");
        return systems == null || systems.isEmpty() ? "HR" : systems.get(0);
    }

    private boolean canEditDepartment(SysUser user, String department) {
        List<String> editable = permService.getEditableDepts(user.getId());
        return editable == null || editable.contains(department);
    }
}
