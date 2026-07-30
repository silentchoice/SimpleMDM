package com.simplemdm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.MdmFieldDefinition;
import com.simplemdm.model.MdmPersonnelSub;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.MdmFieldDefinitionRepository;
import com.simplemdm.repository.MdmPersonnelSubRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FieldDefinitionService {
    private final MdmFieldDefinitionRepository fields;
    private final MdmPersonnelSubRepository records;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public FieldDefinitionService(MdmFieldDefinitionRepository fields,
                                  MdmPersonnelSubRepository records,
                                  PermissionService permissionService,
                                  ObjectMapper objectMapper) {
        this.fields = fields;
        this.records = records;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    public MdmFieldDefinition create(Map<String, Object> body, SysUser user, String systemCode) {
        String tableType = String.valueOf(body.getOrDefault("table_type", "sub"));
        String subType = string(body.get("sub_type"));
        String fieldKey = string(body.get("field_key"));
        String fieldName = string(body.get("field_name"));
        if (subType == null || fieldKey == null || fieldName == null) {
            throw new BusinessException(400, "sub_type、field_key 和 field_name 为必填");
        }
        if (!fieldKey.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw new BusinessException(400, "field_key 只能使用小写英文字母、数字和下划线，且必须以字母开头");
        }
        boolean shared = Boolean.TRUE.equals(body.get("shared"));
        if ("master".equals(tableType) && shared) {
            throw new BusinessException(400, "主表字段不能设置共享");
        }
        fields.findBySystemCodeAndFieldKey(systemCode, fieldKey).ifPresent(existing -> {
            String source = "master".equals(existing.getTableType())
                ? "主表" : existing.getDepartment() + "部门子表";
            throw new BusinessException(400, "字段标识 " + fieldKey + " 已被" + source + "使用");
        });

        MdmFieldDefinition definition = new MdmFieldDefinition();
        definition.setSystemCode(systemCode);
        definition.setDepartment("master".equals(tableType) ? "ALL" : user.getDepartment());
        definition.setTableType(tableType);
        definition.setSubType(subType);
        definition.setFieldKey(fieldKey);
        definition.setFieldName(fieldName);
        definition.setFieldType(String.valueOf(body.getOrDefault("field_type", "string")));
        definition.setRequired(Boolean.TRUE.equals(body.get("required")));
        definition.setSortOrder(number(body.get("sort_order"), 0));
        definition.setShared("sub".equals(tableType) && shared);
        definition.setSystemField(false);
        definition.setCreatedBy(user.getId());
        definition.setCreatedByName(user.getRealName());
        setOptions(definition, body);
        return fields.save(definition);
    }

    public MdmFieldDefinition update(Long id, Map<String, Object> body, SysUser user) {
        MdmFieldDefinition definition = fields.findById(id)
            .orElseThrow(() -> new BusinessException(404, "字段定义不存在"));
        if (!"master".equals(definition.getTableType())
            && !Objects.equals(definition.getDepartment(), user.getDepartment())) {
            throw new BusinessException(403, "只能编辑本部门的字段定义");
        }
        if (body.containsKey("field_name")) definition.setFieldName(string(body.get("field_name")));
        if (body.containsKey("field_type")) {
            String next = string(body.get("field_type"));
            if (Boolean.TRUE.equals(definition.getSystemField()) && !Objects.equals(next, definition.getFieldType())) {
                throw new BusinessException(400, "系统字段类型不可修改");
            }
            definition.setFieldType(next);
        }
        if (body.containsKey("required")) definition.setRequired(Boolean.TRUE.equals(body.get("required")));
        if (body.containsKey("sort_order")) definition.setSortOrder(number(body.get("sort_order"), definition.getSortOrder()));
        if (body.containsKey("shared")) {
            if ("master".equals(definition.getTableType()) && Boolean.TRUE.equals(body.get("shared"))) {
                throw new BusinessException(400, "主表字段不能设置共享");
            }
            definition.setShared("sub".equals(definition.getTableType()) && Boolean.TRUE.equals(body.get("shared")));
        }
        setOptions(definition, body);
        return fields.save(definition);
    }

    @Transactional
    public void deleteSubField(Long id, SysUser user) {
        MdmFieldDefinition definition = fields.findById(id)
            .orElseThrow(() -> new BusinessException(404, "字段定义不存在"));
        if ("master".equals(definition.getTableType()))
            throw new BusinessException(403, "主表字段不可删除");
        if (Boolean.TRUE.equals(definition.getSystemField()))
            throw new BusinessException(403, "系统字段不可删除");
        if (Boolean.TRUE.equals(user.getIsAdmin()))
            throw new BusinessException(403, "主管理员无字段删除权限");
        if (!Objects.equals(user.getDepartment(), definition.getDepartment()))
            throw new BusinessException(403, "只能删除本部门子表字段");
        List<String> editable = permissionService.getEditableDepts(user.getId());
        if (editable == null || !editable.contains(user.getDepartment()))
            throw new BusinessException(403, "无本部门字段删除权限");

        List<MdmPersonnelSub> affected = records.findByOwnerDeptAndSubType(
            definition.getDepartment(), definition.getSubType());
        try {
            for (MdmPersonnelSub record : affected) {
                LinkedHashMap<String, Object> data = objectMapper.readValue(
                    record.getDataJson(), new TypeReference<>() {});
                data.remove(definition.getFieldKey());
                record.setDataJson(objectMapper.writeValueAsString(data));
            }
        } catch (Exception exception) {
            throw new BusinessException(500, "历史子表数据无法清理");
        }
        records.saveAll(affected);
        fields.delete(definition);
    }

    private void setOptions(MdmFieldDefinition definition, Map<String, Object> body) {
        if (!body.containsKey("options")) return;
        try {
            definition.setOptionsJson(objectMapper.writeValueAsString(body.get("options")));
        } catch (Exception exception) {
            throw new BusinessException(400, "字段选项格式无效");
        }
    }

    private String string(Object value) { return value == null ? null : value.toString(); }
    private int number(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
}