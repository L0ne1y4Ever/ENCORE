package com.encore.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.encore.entity.UserAccount;
import com.encore.mapper.UserAccountMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色数据源：登录 id 即 user_account 主键，角色取自该账号的 role 列。
 * checkRoleOr 等路由校验依赖此处返回的角色列表。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final UserAccountMapper userAccountMapper;

    public StpInterfaceImpl(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        UserAccount user = userAccountMapper.selectById(String.valueOf(loginId));
        if (user == null || user.getRole() == null || !"ACTIVE".equals(user.getStatus())) {
            return List.of();
        }
        return List.of(user.getRole());
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }
}
