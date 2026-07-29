package com.simplemdm.service;

import com.simplemdm.dto.PersonnelDTO;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PersonnelService(MdmPersonnelRepository personnelRepo) {
        this.personnelRepo = personnelRepo;
    }

    @Transactional(readOnly = true)
    public Page<MdmPersonnel> listPersonnel(String keyword, String department, int page, int pageSize, List<String> allowedDepts) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        if (allowedDepts == null || allowedDepts.isEmpty()) {
            return personnelRepo.findAll(pageable);
        }
        return personnelRepo.searchByKeywordAndDept(
            (keyword != null && !keyword.isEmpty()) ? keyword : null,
            (department != null && !department.isEmpty()) ? department : null,
            allowedDepts, pageable
        );
    }

    public MdmPersonnel getPersonnel(Long id) {
        return personnelRepo.findById(id).orElse(null);
    }

    public List<String> getDepartments() {
        return personnelRepo.findDistinctDepartments();
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
    public void applyChanges(MdmPersonnel personnel, String changeDataJson) {
        try {
            Map<String, Map<String, Object>> changes = objectMapper.readValue(changeDataJson, Map.class);
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
            personnel.setVersion(personnel.getVersion() + 1);
            personnelRepo.save(personnel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply changes", e);
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
