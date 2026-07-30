package com.simplemdm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simplemdm.dto.PersonnelSubDTO;
import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.MdmPersonnel;
import com.simplemdm.model.MdmPersonnelSub;
import com.simplemdm.model.SysUser;
import com.simplemdm.repository.MdmPersonnelRepository;
import com.simplemdm.repository.MdmPersonnelSubRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PersonnelSubService {

    private final MdmPersonnelSubRepository subRepository;
    private final MdmPersonnelRepository personnelRepository;
    private final DynamicFieldService fieldService;
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;

    public PersonnelSubService(MdmPersonnelSubRepository subRepository,
                               MdmPersonnelRepository personnelRepository,
                               DynamicFieldService fieldService,
                               ObjectMapper objectMapper,
                               PermissionService permissionService) {
        this.subRepository = subRepository;
        this.personnelRepository = personnelRepository;
        this.fieldService = fieldService;
        this.objectMapper = objectMapper;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long personnelId, SysUser user) {
        MdmPersonnel parent = requireParent(personnelId);
        boolean owner = Objects.equals(parent.getOwnerDept(), user.getDepartment());
        List<Map<String, Object>> result = new ArrayList<>();
        for (MdmPersonnelSub record : subRepository.findByPersonnelId(personnelId)) {
            List<com.simplemdm.model.MdmFieldDefinition> definitions = fieldService.visibleSubDefinitions(
                parent.getSystemCode(), parent.getOwnerDept(), user.getDepartment(), record.getSubType());
            if (!owner && definitions.isEmpty()) continue;
            Map<String, Object> raw = read(record.getDataJson());
            LinkedHashMap<String, Object> projected = new LinkedHashMap<>();
            for (com.simplemdm.model.MdmFieldDefinition definition : definitions) {
                if (raw.containsKey(definition.getFieldKey())) {
                    projected.put(definition.getFieldKey(), raw.get(definition.getFieldKey()));
                }
            }
            result.add(toMap(record, projected));
        }
        return result;
    }
    @Transactional
    public Map<String, Object> create(Long personnelId, PersonnelSubDTO dto, SysUser user) {
        MdmPersonnel parent = requireParent(personnelId);
        requireEditor(user, parent.getOwnerDept());
        DynamicFieldService.ValidationResult validated = fieldService.validate(
            parent.getSystemCode(), parent.getOwnerDept(), "sub", dto.subType, dto.data);

        MdmPersonnelSub record = new MdmPersonnelSub();
        record.setSystemCode(parent.getSystemCode());
        record.setPersonnelId(personnelId);
        record.setSubType(dto.subType);
        record.setOwnerDept(parent.getOwnerDept());
        record.setDataJson(write(validated.data()));
        record.setVersion(1);
        return toMap(subRepository.save(record));
    }

    @Transactional
    public Map<String, Object> update(Long personnelId, Long subId, PersonnelSubDTO dto, SysUser user) {
        MdmPersonnelSub record = subRepository.findById(subId)
            .filter(value -> Objects.equals(value.getPersonnelId(), personnelId))
            .orElseThrow(() -> new BusinessException(404, "子表记录不存在"));
        requireEditor(user, record.getOwnerDept());
        if (dto.subType != null && !dto.subType.equals(record.getSubType())) {
            throw new BusinessException(400, "子表记录的数据类型不可修改");
        }
        DynamicFieldService.ValidationResult validated = fieldService.validate(
            record.getSystemCode(), record.getOwnerDept(), "sub", record.getSubType(), dto.data);
        record.setDataJson(write(validated.data()));
        record.setVersion(record.getVersion() + 1);
        return toMap(subRepository.save(record));
    }

    private MdmPersonnel requireParent(Long personnelId) {
        return personnelRepository.findById(personnelId)
            .orElseThrow(() -> new BusinessException(404, "人员不存在"));
    }

    private void requireEditor(SysUser user, String ownerDept) {
        if (!canEdit(user, ownerDept)) {
            throw new BusinessException(403, "只能编辑有权限部门的子表数据");
        }
    }

    private boolean canEdit(SysUser user, String ownerDept) {
        List<String> editable = permissionService.getEditableDepts(user.getId());
        return editable == null || editable.contains(ownerDept);
    }

    private Map<String, Object> toMap(MdmPersonnelSub record) {
        return toMap(record, read(record.getDataJson()));
    }

    private Map<String, Object> toMap(MdmPersonnelSub record, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.getId());
        result.put("personnel_id", record.getPersonnelId());
        result.put("sub_type", record.getSubType());
        result.put("owner_dept", record.getOwnerDept());
        result.put("data", data);
        result.put("version", record.getVersion());
        result.put("created_at", record.getCreatedAt() == null ? null : record.getCreatedAt().toString());
        result.put("updated_at", record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString());
        return result;
    }

    private String write(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception exception) {
            throw new BusinessException(400, "子表数据格式无效");
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception exception) {
            throw new BusinessException(500, "子表数据无法解析");
        }
    }
}
