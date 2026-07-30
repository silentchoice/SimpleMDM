package com.simplemdm.service;

import com.simplemdm.dto.DynamicPersonnelDTO;
import com.simplemdm.model.MdmPersonnel;
import com.simplemdm.repository.MdmPersonnelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PersonnelService {

    private final MdmPersonnelRepository personnelRepo;
    private final DynamicFieldService dynamicFieldService;
    private final ObjectMapper objectMapper;

    public PersonnelService(MdmPersonnelRepository personnelRepo, DynamicFieldService dynamicFieldService,
                            ObjectMapper objectMapper) {
        this.personnelRepo = personnelRepo;
        this.dynamicFieldService = dynamicFieldService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<MdmPersonnel> listPersonnel(String keyword, String department, int page, int pageSize,
                                             List<String> allowedDepts, String systemCode) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        boolean allScope = allowedDepts == null;
        return personnelRepo.searchDynamic(
            (keyword != null && !keyword.isEmpty()) ? keyword : null,
            (department != null && !department.isEmpty()) ? department : null,
            allScope ? List.of("") : allowedDepts, allScope, systemCode, pageable
        );
    }

    public MdmPersonnel getPersonnel(Long id) {
        return personnelRepo.findById(id).orElse(null);
    }

    public List<String> getDepartments(String systemCode) {
        LinkedHashSet<String> departments = new LinkedHashSet<>(
            dynamicFieldService.getDepartments(systemCode));
        departments.addAll(personnelRepo.findDistinctOwnerDepartments());
        return new ArrayList<>(departments);
    }

    @Transactional
    public MdmPersonnel createFromApproval(DynamicPersonnelDTO dto, String systemCode) {
        DynamicFieldService.ValidationResult validated = dynamicFieldService.validate(
            systemCode, dto.ownerDept, "master", "basic", dto.data);
        MdmPersonnel personnel = new MdmPersonnel();
        personnel.setSystemCode(systemCode);
        personnel.setOwnerDept(dto.ownerDept);
        personnel.setDataJson(writeData(validated.data()));
        personnel.setStatus("pending_approval");
        personnel.setVersion(1);
        return personnelRepo.save(personnel);
    }

    @Transactional
    public void applyChanges(MdmPersonnel personnel, String changeDataJson) {
        try {
            Map<String, Map<String, Object>> changes = objectMapper.readValue(changeDataJson, Map.class);
            Map<String, Object> data = readData(personnel);
            String ownerDept = personnel.getOwnerDept();
            for (Map.Entry<String, Map<String, Object>> entry : changes.entrySet()) {
                Object newValue = entry.getValue().get("new");
                if ("owner_dept".equals(entry.getKey())) {
                    ownerDept = (String) newValue;
                } else if (newValue == null) {
                    data.remove(entry.getKey());
                } else {
                    data.put(entry.getKey(), newValue);
                }
            }
            DynamicFieldService.ValidationResult validated = dynamicFieldService.validate(
                personnel.getSystemCode(), ownerDept, "master", "basic", data);
            personnel.setOwnerDept(ownerDept);
            personnel.setDataJson(writeData(validated.data()));
            personnel.setStatus("active");
            personnelRepo.save(personnel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply changes", e);
        }
    }

    public Map<String, Object> computeDiff(MdmPersonnel existing, DynamicPersonnelDTO update) {
        DynamicFieldService.ValidationResult validated = dynamicFieldService.validate(
            existing.getSystemCode(), update.ownerDept, "master", "basic", update.data);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(
            dynamicFieldService.computeDiff(readData(existing), validated.data()));
        if (!Objects.equals(existing.getOwnerDept(), update.ownerDept)) {
            LinkedHashMap<String, Object> departmentChange = new LinkedHashMap<>();
            departmentChange.put("old", existing.getOwnerDept());
            departmentChange.put("new", update.ownerDept);
            LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
            ordered.put("owner_dept", departmentChange);
            ordered.putAll(result);
            return ordered;
        }
        return result;
    }

    public Map<String, Object> readData(MdmPersonnel personnel) {
        try {
            if (personnel.getDataJson() == null || personnel.getDataJson().isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(personnel.getDataJson(), LinkedHashMap.class);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to read personnel data", exception);
        }
    }

    public Map<String, Object> toMap(MdmPersonnel personnel) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", personnel.getId());
        result.put("system_code", personnel.getSystemCode());
        result.put("owner_dept", personnel.getOwnerDept());
        result.put("data", readData(personnel));
        result.put("status", personnel.getStatus());
        result.put("version", personnel.getVersion());
        result.put("created_at", personnel.getCreatedAt() == null ? null : personnel.getCreatedAt().toString());
        result.put("updated_at", personnel.getUpdatedAt() == null ? null : personnel.getUpdatedAt().toString());
        return result;
    }

    private String writeData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to write personnel data", exception);
        }
    }

}
