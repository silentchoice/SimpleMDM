package com.simplemdm.controller;

import com.simplemdm.dto.*;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.simplemdm.security.JwtInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/personnel/{personnelId}/sub")
public class PersonnelSubController {

    private final MdmPersonnelSubRepository subRepo;
    private final MdmPersonnelRepository personnelRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public PersonnelSubController(MdmPersonnelSubRepository subRepo, MdmPersonnelRepository personnelRepo) {
        this.subRepo = subRepo;
        this.personnelRepo = personnelRepo;
    }

    /** Get all sub records visible to current user for this personnel */
    @GetMapping
    public ApiResponse list(@PathVariable Long personnelId) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        MdmPersonnel p = personnelRepo.findById(personnelId).orElse(null);
        if (p == null) return ApiResponse.error(404, "人员不存在");

        List<MdmPersonnelSub> all = subRepo.findByPersonnelId(personnelId);

        // Filter by visibility: own dept sees all, others see only shared
        boolean isOwnerDept = user.getDepartment() != null && user.getDepartment().equals(p.getDepartment());
        List<Map<String, Object>> items = new ArrayList<>();
        for (MdmPersonnelSub sub : all) {
            if (!isOwnerDept && !"shared".equals(sub.getVisibility())) continue;
            items.add(toMap(sub));
        }
        return ApiResponse.ok(items);
    }

    /** Create a sub record (only by owner department, needs EDIT perm) */
    @PostMapping
    public ApiResponse create(@PathVariable Long personnelId, @RequestBody PersonnelSubDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        MdmPersonnel p = personnelRepo.findById(personnelId).orElse(null);
        if (p == null) return ApiResponse.error(404, "人员不存在");

        // Only same department can create sub records
        if (user.getDepartment() == null || !user.getDepartment().equals(p.getDepartment())) {
            return ApiResponse.error(403, "只能为同部门人员添加子表数据");
        }

        MdmPersonnelSub sub = new MdmPersonnelSub();
        sub.setPersonnelId(personnelId);
        sub.setSubType(dto.subType);
        sub.setDataJson(dto.dataJson);
        sub.setOwnerDept(user.getDepartment());
        sub.setVisibility("private");
        sub.setVersion(1);
        sub = subRepo.save(sub);

        return ApiResponse.ok("子表数据已创建", toMap(sub));
    }

    /** Update a sub record */
    @PutMapping("/{subId}")
    public ApiResponse update(@PathVariable Long personnelId, @PathVariable Long subId,
                              @RequestBody PersonnelSubDTO dto) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        MdmPersonnelSub sub = subRepo.findById(subId).orElse(null);
        if (sub == null || !sub.getPersonnelId().equals(personnelId))
            return ApiResponse.error(404, "子表记录不存在");

        if (user.getDepartment() == null || !user.getDepartment().equals(sub.getOwnerDept())) {
            return ApiResponse.error(403, "只能编辑本部门的子表数据");
        }

        if (dto.dataJson != null) sub.setDataJson(dto.dataJson);
        if (dto.subType != null) sub.setSubType(dto.subType);
        if (dto.visibility != null) sub.setVisibility(dto.visibility);
        sub.setVersion(sub.getVersion() + 1);
        sub = subRepo.save(sub);

        return ApiResponse.ok("子表数据已更新", toMap(sub));
    }

    private Map<String, Object> toMap(MdmPersonnelSub sub) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", sub.getId());
        m.put("personnel_id", sub.getPersonnelId());
        m.put("sub_type", sub.getSubType());
        m.put("data_json", sub.getDataJson());
        m.put("owner_dept", sub.getOwnerDept());
        m.put("visibility", sub.getVisibility());
        m.put("version", sub.getVersion());
        m.put("created_at", sub.getCreatedAt() != null ? sub.getCreatedAt().toString() : null);
        m.put("updated_at", sub.getUpdatedAt() != null ? sub.getUpdatedAt().toString() : null);
        return m;
    }
}
