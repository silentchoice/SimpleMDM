package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.simplemdm.security.JwtInterceptor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dept-fields")
public class FieldDefinitionController {

    private final MdmFieldDefinitionRepository fieldRepo;

    public FieldDefinitionController(MdmFieldDefinitionRepository fieldRepo) {
        this.fieldRepo = fieldRepo;
    }

    /** List all field definitions for current user's department */
    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String subType) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        String dept = user.getDepartment();
        if (dept == null) return ApiResponse.error(400, "当前用户无部门");

        List<MdmFieldDefinition> fields;
        if (!subType.isEmpty()) {
            fields = fieldRepo.findByDepartmentAndSubTypeOrderBySortOrder(dept, subType);
        } else {
            fields = fieldRepo.findByDepartmentOrderBySubTypeAscSortOrder(dept);
        }

        List<Map<String, Object>> items = fields.stream().map(this::toMap).toList();
        return ApiResponse.ok(items);
    }

    /** List sub_types for current user's department */
    @GetMapping("/sub-types")
    public ApiResponse subTypes() {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        String dept = user.getDepartment();
        if (dept == null) return ApiResponse.error(400, "当前用户无部门");
        return ApiResponse.ok(fieldRepo.findDistinctSubTypesByDepartment(dept));
    }

    /** Get field definitions for a specific sub_type (any dept — for cross-dept viewing) */
    @GetMapping("/by-type")
    public ApiResponse getByType(@RequestParam String subType,
                                  @RequestParam String department) {
        List<MdmFieldDefinition> fields = fieldRepo.findByDepartmentAndSubTypeOrderBySortOrder(department, subType);
        return ApiResponse.ok(fields.stream().map(this::toMap).toList());
    }

    /** Create a field definition (only for own department) */
    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> body) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        String dept = user.getDepartment();
        if (dept == null) return ApiResponse.error(400, "当前用户无部门");

        String subType = (String) body.get("sub_type");
        String fieldName = (String) body.get("field_name");
        String fieldType = body.getOrDefault("field_type", "string").toString();
        Boolean required = (Boolean) body.getOrDefault("required", false);
        Integer sortOrder = (Integer) body.getOrDefault("sort_order", 0);

        if (subType == null || fieldName == null) {
            return ApiResponse.error(400, "sub_type 和 field_name 为必填");
        }

        MdmFieldDefinition def = new MdmFieldDefinition();
        def.setDepartment(dept);
        def.setSubType(subType);
        def.setFieldName(fieldName);
        def.setFieldType(fieldType);
        def.setRequired(required);
        def.setSortOrder(sortOrder);
        def.setCreatedBy(user.getId());
        def.setCreatedByName(user.getRealName());
        def = fieldRepo.save(def);

        return ApiResponse.ok("字段已创建", toMap(def));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        MdmFieldDefinition def = fieldRepo.findById(id).orElse(null);
        if (def == null) return ApiResponse.error(404, "字段定义不存在");
        if (!def.getDepartment().equals(user.getDepartment())) {
            return ApiResponse.error(403, "只能编辑本部门的字段定义");
        }

        if (body.containsKey("field_name")) def.setFieldName((String) body.get("field_name"));
        if (body.containsKey("field_type")) def.setFieldType((String) body.get("field_type"));
        if (body.containsKey("required")) def.setRequired((Boolean) body.get("required"));
        if (body.containsKey("sort_order")) def.setSortOrder((Integer) body.get("sort_order"));
        def = fieldRepo.save(def);

        return ApiResponse.ok("字段已更新", toMap(def));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        MdmFieldDefinition def = fieldRepo.findById(id).orElse(null);
        if (def == null) return ApiResponse.error(404, "字段定义不存在");
        if (!def.getDepartment().equals(user.getDepartment())) {
            return ApiResponse.error(403, "只能删除本部门的字段定义");
        }
        // Soft delete via status — for now, warn that delete requires admin review
        return ApiResponse.error(403, "删除操作需管理员审核");
    }

    private Map<String, Object> toMap(MdmFieldDefinition f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", f.getId());
        m.put("department", f.getDepartment());
        m.put("sub_type", f.getSubType());
        m.put("field_name", f.getFieldName());
        m.put("field_type", f.getFieldType());
        m.put("required", f.getRequired());
        m.put("sort_order", f.getSortOrder());
        m.put("created_by", f.getCreatedBy());
        m.put("created_by_name", f.getCreatedByName());
        m.put("created_at", f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
        return m;
    }
}
