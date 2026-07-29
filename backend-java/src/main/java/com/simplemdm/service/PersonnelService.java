package com.simplemdm.service;

import com.simplemdm.dto.PersonnelDTO;
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

    public List<String> getDepartments() {
        List<String> dynamicDepartments = personnelRepo.findDistinctOwnerDepartments();
        return dynamicDepartments.isEmpty() ? personnelRepo.findDistinctDepartments() : dynamicDepartments;
    }

    public MdmPersonnel getByEmployeeCode(String employeeCode) {
        return personnelRepo.findByEmployeeCode(employeeCode).orElse(null);
    }

    @Transactional
    public MdmPersonnel createFromApproval(PersonnelDTO dto) {
        MdmPersonnel p = new MdmPersonnel();
        p.setEmployeeCode(dto.employeeCode);
        p.setName(dto.name);
        p.setGender(dto.gender);
        p.setDepartment(dto.department);
        p.setPosition(dto.position);
        p.setPhone(dto.phone);
        p.setEmail(dto.email);
        p.setStatus("pending_approval");
        p.setVersion(1);
        return personnelRepo.save(p);
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
            if (personnel.getOwnerDept() != null) {
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
                return;
            }
            for (Map.Entry<String, Map<String, Object>> entry : changes.entrySet()) {
                String field = entry.getKey();
                Object newValue = entry.getValue().get("new");
                switch (field) {
                    case "name": personnel.setName((String) newValue); break;
                    case "gender": personnel.setGender((String) newValue); break;
                    case "department": personnel.setDepartment((String) newValue); break;
                    case "position": personnel.setPosition((String) newValue); break;
                    case "phone": personnel.setPhone((String) newValue); break;
                    case "email": personnel.setEmail((String) newValue); break;
                    case "employee_code": personnel.setEmployeeCode((String) newValue); break;
                }
            }
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

    public Map<String, Object> computeDiff(MdmPersonnel existing, PersonnelDTO update) {
        Map<String, Map<String, Object>> diff = new HashMap<>();
        compareField(diff, "name", existing.getName(), update.name);
        compareField(diff, "gender", existing.getGender(), update.gender);
        compareField(diff, "department", existing.getDepartment(), update.department);
        compareField(diff, "position", existing.getPosition(), update.position);
        compareField(diff, "phone", existing.getPhone(), update.phone);
        compareField(diff, "email", existing.getEmail(), update.email);
        compareField(diff, "employee_code", existing.getEmployeeCode(), update.employeeCode);

        if (diff.isEmpty()) return null;

        Map<String, Object> result = new HashMap<>();
        result.put("diff", diff);
        return result;
    }

    private void compareField(Map<String, Map<String, Object>> diff, String field, Object oldVal, Object newVal) {
        if (newVal != null && !Objects.equals(oldVal, newVal)) {
            Map<String, Object> change = new HashMap<>();
            change.put("old", oldVal);
            change.put("new", newVal);
            diff.put(field, change);
        }
    }
}
