package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.security.*;
import com.simplemdm.service.*;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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

        List<Map<String, Object>> items = result.getContent().stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("system_code", p.getSystemCode());
            m.put("employee_code", p.getEmployeeCode());
            m.put("name", p.getName());
            m.put("gender", p.getGender());
            m.put("department", p.getDepartment());
            m.put("position", p.getPosition());
            m.put("phone", p.getPhone());
            m.put("email", p.getEmail());
            m.put("status", p.getStatus());
            m.put("version", p.getVersion());
            m.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            m.put("updated_at", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return ApiResponse.ok(new PageResult<>(items, result.getTotalElements(), page, pageSize));
    }

    @GetMapping("/departments")
    public ApiResponse departments() {
        return ApiResponse.ok(personnelService.getDepartments());
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        MdmPersonnel p = personnelService.getPersonnel(id);
        if (p == null) return ApiResponse.error(404, "人员不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("employee_code", p.getEmployeeCode());
        m.put("name", p.getName());
        m.put("gender", p.getGender());
        m.put("department", p.getDepartment());
        m.put("position", p.getPosition());
        m.put("phone", p.getPhone());
        m.put("email", p.getEmail());
        m.put("status", p.getStatus());
        m.put("version", p.getVersion());
        m.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        m.put("updated_at", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return ApiResponse.ok(m);
    }

    @PostMapping
    @RequirePerm("EDIT")
    public ApiResponse create(@RequestBody PersonnelDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();

        // Check employee_code unique
        if (personnelService.getByEmployeeCode(dto.employeeCode) != null) {
            return ApiResponse.error(400, "工号 " + dto.employeeCode + " 已存在");
        }

        WfApproval approval = approvalService.createApprovalForCreate(user.getId(), dto);
        Map<String, Object> data = new HashMap<>();
        data.put("personnel_id", approval.getPersonnelId());
        data.put("approval_id", approval.getId());
        return ApiResponse.ok("提交成功，请等待审批", data);
    }

    @PutMapping("/{id}")
    @RequirePerm("EDIT")
    public ApiResponse update(@PathVariable Long id, @RequestBody PersonnelDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        WfApproval approval = approvalService.createApprovalForUpdate(id, user.getId(), dto);
        if (approval == null) return ApiResponse.ok("没有变更需要提交", null);
        Map<String, Object> data = new HashMap<>();
        data.put("personnel_id", id);
        data.put("approval_id", approval.getId());
        return ApiResponse.ok("变更已提交，请等待审批", data);
    }
}
