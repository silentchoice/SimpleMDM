package com.simplemdm.repository;

import com.simplemdm.model.SysUserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SysUserPermissionRepository extends JpaRepository<SysUserPermission, Long> {
    List<SysUserPermission> findByUserId(Long userId);
    List<SysUserPermission> findByUserIdAndPermType(Long userId, String permType);
    void deleteByUserIdAndId(Long userId, Long id);
}
