package com.encore.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.LoginRequest;
import com.encore.dto.LoginResponse;
import com.encore.dto.RegisterRequest;
import com.encore.dto.UpdateCurrentUserRequest;
import com.encore.dto.UserProfileResponse;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.UserAccountMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount user = findByUsername(request.username());
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginResponse(tokenInfo.getTokenName(), tokenInfo.getTokenValue(), toProfile(user));
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = clean(request.username());
        String password = clean(request.password());
        String displayName = clean(request.displayName());
        if (!StringUtils.hasText(username) || username.length() < 3 || username.length() > 32) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号长度需为 3 到 32 个字符");
        }
        if (!StringUtils.hasText(password) || password.length() < 3 || password.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度需为 3 到 64 个字符");
        }
        if (!StringUtils.hasText(displayName) || displayName.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称不能为空");
        }
        if (findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        UserAccount user = new UserAccount();
        user.setId(generateUserId());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("user");
        user.setDisplayName(displayName);
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);

        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginResponse(tokenInfo.getTokenName(), tokenInfo.getTokenValue(), toProfile(user));
    }

    public void logout() {
        StpUtil.logout();
    }

    public UserProfileResponse currentUser() {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录用户不存在");
        }
        return toProfile(user);
    }

    @Transactional
    public UserProfileResponse updateCurrentUser(UpdateCurrentUserRequest request) {
        String userId = StpUtil.getLoginIdAsString();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录用户不存在");
        }

        String displayName = clean(request.displayName());
        if (!StringUtils.hasText(displayName) || displayName.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称不能为空");
        }

        user.setDisplayName(displayName);
        user.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.updateById(user);
        return toProfile(user);
    }

    private UserAccount findByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)
                .last("limit 1"));
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String generateUserId() {
        String id;
        do {
            id = "u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (userAccountMapper.selectById(id) != null);
        return id;
    }

    private UserProfileResponse toProfile(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getRole(), user.getDisplayName());
    }
}
