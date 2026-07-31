package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.*;
import com.simplemdm.repository.*;
import com.simplemdm.security.JwtInterceptor;
import com.simplemdm.service.PermissionService;
import com.simplemdm.service.FieldDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dept-fields")
public class FieldDefinitionController {

    private final MdmFieldDefinitionRepository fieldRepo;
    private final PermissionService permService;
    private final FieldDefinitionService fieldDefinitionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FieldDefinitionController(MdmFieldDefinitionRepository fieldRepo, PermissionService permService,
                                     FieldDefinitionService fieldDefinitionService) {
        this.fieldRepo = fieldRepo;
        this.permService = permService;
        this.fieldDefinitionService = fieldDefinitionService;
    }

    private String getUserSystemCode() {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        List<String> systems = permService.getPermittedSystems(user.getId(), "VIEW");
        return (systems == null || systems.isEmpty()) ? "HR" : systems.get(0);
    }

    /** List all field definitions for current user's department.
     *  table_type=master returns shared master fields (from all depts).
     *  table_type=sub returns department-specific sub fields. */
    @GetMapping
    public ApiResponse list(@RequestParam(defaultValue = "") String subType,
                            @RequestParam(defaultValue = "") String tableType) {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
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
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        String dept = user.getDepartment();
        String sysCode = getUserSystemCode();
        if (dept == null) return ApiResponse.error(400, "当前用户无部门");
        if ("master".equals(tableType)) {
            return ApiResponse.ok(fieldRepo.findDistinctSubTypesByTableTypeAndSystemCode("master", sysCode));
        }
        return ApiResponse.ok(fieldRepo.findDistinctSubTypesByDepartmentAndTableTypeAndSystemCode(
            dept, "sub", sysCode));
    }

    /** Get field definitions for a specific sub_type (any dept — for cross-dept viewing) */
    @GetMapping("/by-type")
    public ApiResponse getByType(@RequestParam String subType,
                                  @RequestParam String department) {
        List<MdmFieldDefinition> fields = fieldRepo.findByDepartmentAndSubTypeAndSystemCodeOrderBySortOrder(
            department, subType, getUserSystemCode());
        return ApiResponse.ok(fields.stream().map(this::toMap).toList());
    }

    /** Create a field definition (only for own department) */
    @PostMapping
    public ApiResponse create(@RequestBody Map<String, Object> body) {
        SysUser user = JwtInterceptor.LEGACY_CURRENT_USER.get();
        MdmFieldDefinition definition = fieldDefinitionService.create(body, user, getUserSystemCode());
        return ApiResponse.ok("字段已创建", toMap(definition));
    }
    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MdmFieldDefinition definition = fieldDefinitionService.update(
            id, body, JwtInterceptor.LEGACY_CURRENT_USER.get());
        return ApiResponse.ok("字段已更新", toMap(definition));
    }
    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        fieldDefinitionService.deleteSubField(id, JwtInterceptor.LEGACY_CURRENT_USER.get());
        return ApiResponse.ok("字段及历史数据已删除", null);
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
        m.put("shared", f.getShared());
        m.put("created_by", f.getCreatedBy());
        m.put("created_by_name", f.getCreatedByName());
        m.put("created_at", f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
        return m;
    }
}
