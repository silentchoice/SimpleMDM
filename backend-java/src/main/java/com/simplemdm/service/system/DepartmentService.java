package com.simplemdm.service.system;

import com.simplemdm.exception.BusinessException;
import com.simplemdm.model.system.Department;
import com.simplemdm.model.system.SystemEntity;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.repository.system.SystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final SystemRepository systems;
    private final DepartmentRepository departments;

    public DepartmentService(SystemRepository systems, DepartmentRepository departments) {
        this.systems = systems;
        this.departments = departments;
    }

    @Transactional
    public Department create(Long systemId, Long parentId, String code, String name) {
        SystemEntity system = requiredSystem(systemId);
        Department parent = parentId == null ? null : requiredDepartment(parentId);
        if (parent != null) {
            requireSameSystem(system, parent);
        }

        Department department = departments.saveAndFlush(Department.create(system, parent, code, name));
        String path = parent == null
            ? "/" + department.getId() + "/"
            : parent.getPath() + department.getId() + "/";
        department.relocate(parent, path, parent == null ? 1 : parent.getLevel() + 1);
        return department;
    }

    @Transactional
    public void move(Long departmentId, Long newParentId) {
        Department department = requiredDepartment(departmentId);
        Department newParent = newParentId == null ? null : requiredDepartment(newParentId);
        if (newParent != null) {
            requireSameSystem(department.getSystem(), newParent);
            if (newParent.getPath().startsWith(department.getPath())) {
                throw new BusinessException(400, "Department move would create a cycle");
            }
        }

        String oldPath = department.getPath();
        int levelChange = (newParent == null ? 1 : newParent.getLevel() + 1) - department.getLevel();
        String newPath = newParent == null
            ? "/" + department.getId() + "/"
            : newParent.getPath() + department.getId() + "/";
        List<Department> descendants = departments.findByPathStartingWith(oldPath);
        for (Department descendant : descendants) {
            String suffix = descendant.getPath().substring(oldPath.length());
            descendant.relocate(descendant.getId().equals(department.getId()) ? newParent : descendant.getParent(),
                newPath + suffix, descendant.getLevel() + levelChange);
        }
    }

    private SystemEntity requiredSystem(Long systemId) {
        return systems.findById(systemId)
            .orElseThrow(() -> new BusinessException(404, "System not found"));
    }

    private Department requiredDepartment(Long departmentId) {
        return departments.findById(departmentId)
            .orElseThrow(() -> new BusinessException(404, "Department not found"));
    }

    private void requireSameSystem(SystemEntity system, Department department) {
        if (!system.getId().equals(department.getSystem().getId())) {
            throw new BusinessException(400, "Parent department must belong to the same system");
        }
    }
}
