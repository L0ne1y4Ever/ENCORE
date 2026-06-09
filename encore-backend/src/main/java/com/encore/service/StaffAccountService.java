package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.AdminStaffUserResponse;
import com.encore.dto.CreateStaffUserRequest;
import com.encore.dto.ResetStaffPasswordRequest;
import com.encore.dto.UpdateStaffUserRequest;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.UserAccountMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class StaffAccountService {
    private static final Set<String> STAFF_ROLES = Set.of("admin", "checker", "sysadmin");
    private static final Set<String> EDITABLE_STAFF_ROLES = Set.of("admin", "checker");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountService(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminStaffUserResponse> listStaffUsers() {
        ensureSysadminRole();
        return userAccountMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                        .in(UserAccount::getRole, STAFF_ROLES)
                        .orderByAsc(UserAccount::getRole)
                        .orderByAsc(UserAccount::getUsername))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminStaffUserResponse createStaffUser(CreateStaffUserRequest request) {
        ensureSysadminRole();
        String username = CredentialPolicy.normalizeUsername(request.username());
        String password = CredentialPolicy.validatePassword(request.password(), username);
        String displayName = CredentialPolicy.normalizeDisplayName(request.displayName());
        String role = normalizeEditableRole(request.role());
        String status = normalizeStatus(request.status(), "ACTIVE");
        if (findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        UserAccount user = new UserAccount();
        user.setId(generateUserId());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setDisplayName(displayName);
        user.setStatus(status);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        return toResponse(user);
    }

    @Transactional
    public AdminStaffUserResponse updateStaffUser(String userId, UpdateStaffUserRequest request) {
        ensureSysadminRole();
        UserAccount user = getUser(userId);
        ensureEditable(user);
        String displayName = CredentialPolicy.normalizeDisplayName(request.displayName());
        user.setDisplayName(displayName);
        user.setRole(normalizeEditableRole(request.role()));
        user.setStatus(normalizeStatus(request.status(), user.getStatus()));
        user.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.updateById(user);
        return toResponse(user);
    }

    @Transactional
    public AdminStaffUserResponse resetPassword(String userId, ResetStaffPasswordRequest request) {
        ensureSysadminRole();
        UserAccount user = getUser(userId);
        ensureEditable(user);
        String password = CredentialPolicy.validatePassword(request.password(), user.getUsername());
        user.setPassword(passwordEncoder.encode(password));
        user.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.updateById(user);
        return toResponse(user);
    }

    private void ensureSysadminRole() {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !"sysadmin".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅系统管理员可管理员工账号");
        }
    }

    private UserAccount getUser(String userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !STAFF_ROLES.contains(user.getRole())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工账号不存在");
        }
        return user;
    }

    private void ensureEditable(UserAccount user) {
        if (!EDITABLE_STAFF_ROLES.contains(user.getRole())) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统管理员账号不可在此修改");
        }
    }

    private UserAccount findByUsername(String username) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)
                .last("limit 1"));
    }

    private String normalizeEditableRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不能为空");
        }
        String normalized = role.trim().toLowerCase();
        if (!EDITABLE_STAFF_ROLES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "员工角色仅支持 admin 或 checker");
        }
        return normalized;
    }

    private String normalizeStatus(String status, String fallback) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : fallback;
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号状态仅支持 ACTIVE 或 INACTIVE");
        }
        return normalized;
    }

    private AdminStaffUserResponse toResponse(UserAccount user) {
        return new AdminStaffUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getDisplayName(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                EDITABLE_STAFF_ROLES.contains(user.getRole())
        );
    }

    private String generateUserId() {
        String id;
        do {
            id = "u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (userAccountMapper.selectById(id) != null);
        return id;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
