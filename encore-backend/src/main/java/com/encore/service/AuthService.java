package com.encore.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.encore.common.ErrorCode;
import com.encore.dto.LoginRequest;
import com.encore.dto.LoginResponse;
import com.encore.dto.UserProfileResponse;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {
    private final UserAccountMapper userAccountMapper;

    public AuthService(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount user = findByUsername(request.username());
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !request.password().equals(user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

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
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录用户不存在");
        }
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

    private UserProfileResponse toProfile(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getRole(), user.getDisplayName());
    }
}
