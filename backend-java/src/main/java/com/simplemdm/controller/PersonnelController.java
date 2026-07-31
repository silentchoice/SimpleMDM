package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.security.*;
import com.simplemdm.service.*;
import com.simplemdm.exception.BusinessException;
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
        if (department == null || department.isBlank()) {
            return ApiResponse.error(400, "必须选择部门");
        }
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        String systemCode = currentSystem(user, "VIEW");
        List<String> viewable = permService.getConcreteViewableDepts(user.getId(), systemCode);
        if (!viewable.contains(department)) {
            return ApiResponse.error(403, "无权查看该部门主数据");
        }
        Page<MdmPersonnel> result = personnelService.listPersonnel(
            keyword, department, page, pageSize, List.of(department), systemCode);
        List<Map<String, Object>> items = result.getContent().stream()
            .map(personnelService::toMap)
            .collect(Collectors.toList());
        return ApiResponse.ok(new PageResult<>(items, result.getTotalElements(), page, pageSize));
    }
    @GetMapping("/departments")
    public ApiResponse departments() {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        String systemCode = currentSystem(user, "VIEW");
        return ApiResponse.ok(permService.getConcreteViewableDepts(user.getId(), systemCode));
    }
    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        try {
            MdmPersonnel personnel = personnelService.requireViewablePersonnel(id, user);
            return ApiResponse.ok(personnelService.toMap(personnel));
        } catch (BusinessException exception) {
            return ApiResponse.error(exception.getCode(), exception.getMessage());
        }
    }
    @PostMapping
    @RequirePerm("EDIT")
    public ApiResponse create(@Valid @RequestBody DynamicPersonnelDTO dto) {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        if (!Objects.equals(user.getDepartment(), dto.ownerDept)) {
            return ApiResponse.error(403, "只能维护所属部门主数据");
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
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        MdmPersonnel existing = personnelService.getPersonnel(id);
        if (existing == null) return ApiResponse.error(404, "人员不存在");
        if (!Objects.equals(user.getDepartment(), existing.getOwnerDept())
            || !Objects.equals(user.getDepartment(), dto.ownerDept)) {
            return ApiResponse.error(403, "只能维护所属部门主数据");
        }
        WfApproval approval = approvalService.createApprovalForUpdate(id, user.getId(), dto);
        if (approval == null) return ApiResponse.ok("没有变更需要提交", null);
        Map<String, Object> data = new HashMap<>();
        data.put("personnel_id", id);
        data.put("approval_id", approval.getId());
        return ApiResponse.ok("变更已提交，请等待审批", data);
    }

    private String currentSystem(SysUser user) {
        return currentSystem(user, "EDIT");
    }

    private String currentSystem(SysUser user, String permissionType) {
        List<String> systems = permService.getPermittedSystems(user.getId(), permissionType);
        return systems == null || systems.isEmpty() ? "HR" : systems.get(0);
    }

}
