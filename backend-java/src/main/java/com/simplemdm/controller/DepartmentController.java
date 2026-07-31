package com.simplemdm.controller;

import com.simplemdm.dto.ApiResponse;
import com.simplemdm.model.system.Department;
import com.simplemdm.repository.system.DepartmentRepository;
import com.simplemdm.service.system.AuthorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DepartmentRepository departments;
    private final AuthorizationService authorization;

    public DepartmentController(DepartmentRepository departments, AuthorizationService authorization) {
        this.departments = departments;
        this.authorization = authorization;
    }

    @GetMapping("/tree")
    public ApiResponse tree() {
        var user = SystemController.currentUser();
        Set<Long> visible = authorization.viewableDepartmentIds(user.getId());
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Department> all = departments.findAll();
        for (Department department : all) {
            if (user.getSystemId().equals(department.getSystem().getId()) && visible.contains(department.getId())) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", department.getId());
                node.put("path", department.getPath());
                node.put("level", department.getLevel());
                node.put("children", new ArrayList<Map<String, Object>>());
                nodes.put(department.getId(), node);
            }
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Department department : all) {
            Map<String, Object> node = nodes.get(department.getId());
            if (node == null) continue;
            Department parent = department.getParent();
            Map<String, Object> parentNode = parent == null ? null : nodes.get(parent.getId());
            if (parentNode == null) roots.add(node);
            else children(parentNode).add(node);
        }
        return ApiResponse.ok(roots);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> children(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("children");
    }
}