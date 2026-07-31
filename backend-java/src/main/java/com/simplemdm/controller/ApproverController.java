package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.simplemdm.security.JwtInterceptor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/approvers")
public class ApproverController {

    private final SysApproverDeptRepository approverDeptRepo;
    private final SysUserRepository userRepo;

    public ApproverController(SysApproverDeptRepository adr, SysUserRepository ur) {
        this.approverDeptRepo = adr; this.userRepo = ur;
    }

    @GetMapping
    public ApiResponse list() {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        List<SysApproverDept> all = approverDeptRepo.findAll();
        List<Map<String, Object>> items = new ArrayList<>();
        for (SysApproverDept ad : all) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ad.getId()); m.put("user_id", ad.getUserId());
            m.put("department", ad.getDepartment());
            m.put("user_name", userRepo.findById(ad.getUserId()).map(SysUser::getRealName).orElse(""));
            items.add(m);
        }
        return ApiResponse.ok(items);
    }

    @PostMapping
    public ApiResponse assign(@RequestBody ApproverDeptDTO dto) {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        List<SysApproverDept> existing = approverDeptRepo.findByUserIdAndDepartment(dto.userId, dto.department);
        if (!existing.isEmpty()) return ApiResponse.error(400, "该审批人已分配到此部门");
        SysApproverDept ad = new SysApproverDept();
        ad.setUserId(dto.userId); ad.setDepartment(dto.department);
        ad = approverDeptRepo.save(ad);
        return ApiResponse.ok("审批人已分配", Map.of("id", ad.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse remove(@PathVariable Long id) {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        if (!Boolean.TRUE.equals(user.getIsAdmin())) return ApiResponse.error(403, "仅管理员可操作");
        return ApiResponse.error(403, "删除操作需管理员审核");
    }
}
