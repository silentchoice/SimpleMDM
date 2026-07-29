package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dept-fields")
public class FieldDefinitionController {

    private final MdmFieldDefinitionRepository fieldRepo;
    private final PermissionService permService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FieldDefinitionController(MdmFieldDefinitionRepository fieldRepo, PermissionService permService) {
        this.fieldRepo = fieldRepo;
        this.permService = permService;
    }

    private String getUserSystemCode() {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        List<String> systems = permService.getPermittedSystems(user.getId(), "VIEW");
        return (systems == null || systems.isEmpty()) ? "HR" : systems.get(0);
    }

    /** List all field definitions for current user's department.
     *  table_type=master returns shared master fields (from all depts).
     *  table_type=sub returns department-specific sub fields. */
    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String subType,
                            @RequestParam(defaultValue = "") String tableType) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        String dept = user.getDepartment();
        if (dept == null) return ApiResponse.error(400, "当前用户无部门");

        String sysCode = getUserSystemCode();
        List<MdmFieldDefinition> fields;
        if ("master".equals(tableType)) {
            fields = fieldRepo.findByTableTypeAndSystemCodeOrderBySubTypeAscSortOrder("master", sysCode);
        } else if (!tableType.isEmpty()) {
            fields = fieldRepo.findByDepartmentAndTableTypeAndSystemCodeOrderBySubTypeAscSortOrder(dept, tableType, sysCode);
        } else if (!subType.isEmpty()) {
            fields = fieldRepo.findByDepartmentAndSubTypeAndSystemCodeOrderBySortOrder(dept, subType, sysCode);
        } else {
            fields = fieldRepo.findByDepartmentAndSystemCodeOrderBySubTypeAscSortOrder(dept, sysCode);
        }

        List<Map<String, Object>> items = fields.stream().map(this::toMap).toList();
        return ApiResponse.ok(items);
    }

    /** List sub_types for current user's department */
    @GetMapping("/sub-types")
    public ApiResponse subTypes(@RequestParam(defaultValue = "sub") String tableType) {
        SysUser user = JwtInterceptor.CURRENT_USER.get();
        String dept = user.getDepartment();
        String sysCode = getUserSystemCode();
        if (dept == null) return ApiResponse.error(400, "当前用户无部门");
        if ("master".equals(tableType)) {
            return ApiResponse.ok(fieldRepo.findDistinctSubTypesByDepartmentAndTableTypeAndSystemCode(dept, "master", sysCode));
        }
        return ApiResponse.ok(fieldRepo.findDistinctSubTypesByDepartmentAndSystemCode(dept, sysCode));
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

        String tableType = body.getOrDefault("table_type", "sub").toString();
        String subType = (String) body.get("sub_type");
        String fieldKey = (String) body.get("field_key");
        String fieldName = (String) body.get("field_name");
        String fieldType = body.getOrDefault("field_type", "string").toString();
        Boolean required = (Boolean) body.getOrDefault("required", false);
        Integer sortOrder = (Integer) body.getOrDefault("sort_order", 0);

        if (subType == null || fieldKey == null || fieldName == null) {
            return ApiResponse.error(400, "sub_type、field_key 和 field_name 为必填");
        }
        if (!fieldKey.matches("^[a-z][a-z0-9_]{1,63}$")) {
            return ApiResponse.error(400, "field_key 只能使用小写英文字母、数字和下划线，且必须以字母开头");
        }

        String sysCode = getUserSystemCode();
        String definitionDept = "master".equals(tableType) ? "ALL" : dept;
        if (fieldRepo.existsBySystemCodeAndDepartmentAndTableTypeAndSubTypeAndFieldKey(
            sysCode, definitionDept, tableType, subType, fieldKey)) {
            return ApiResponse.error(400, "字段标识 " + fieldKey + " 已存在");
        }
        MdmFieldDefinition def = new MdmFieldDefinition();
        def.setSystemCode(sysCode);
        def.setDepartment(definitionDept);
        def.setTableType(tableType);
        def.setSubType(subType);
        def.setFieldKey(fieldKey);
        def.setFieldName(fieldName);
        def.setFieldType(fieldType);
        def.setRequired(required);
        def.setSortOrder(sortOrder);
        def.setCreatedBy(user.getId());
        def.setCreatedByName(user.getRealName());
        if (body.containsKey("options")) {
            try {
                def.setOptionsJson(objectMapper.writeValueAsString(body.get("options")));
            } catch (Exception exception) {
                return ApiResponse.error(400, "字段选项格式无效");
            }
        }
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
        if (body.containsKey("options")) {
            try {
                def.setOptionsJson(objectMapper.writeValueAsString(body.get("options")));
            } catch (Exception exception) {
                return ApiResponse.error(400, "字段选项格式无效");
            }
        }
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
        m.put("table_type", f.getTableType());
        m.put("department", f.getDepartment());
        m.put("sub_type", f.getSubType());
        m.put("field_key", f.getFieldKey());
        m.put("field_name", f.getFieldName());
        m.put("field_type", f.getFieldType());
        m.put("required", f.getRequired());
        m.put("sort_order", f.getSortOrder());
        try {
            m.put("options", f.getOptionsJson() == null
                ? List.of()
                : objectMapper.readValue(f.getOptionsJson(), List.class));
        } catch (Exception exception) {
            m.put("options", List.of());
        }
        m.put("system_field", f.getSystemField());
        m.put("created_by", f.getCreatedBy());
        m.put("created_by_name", f.getCreatedByName());
        m.put("created_at", f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
        return m;
    }
}
