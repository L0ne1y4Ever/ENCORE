package com.encore.satoken;

import com.encore.entity.UserAccount;
import com.encore.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {
    @Mock
    private UserAccountMapper userAccountMapper;

    @Test
    void getRoleListReturnsAccountRole() {
        StpInterfaceImpl stpInterface = new StpInterfaceImpl(userAccountMapper);
        UserAccount admin = new UserAccount();
        admin.setId("u-admin");
        admin.setRole("admin");
        when(userAccountMapper.selectById("u-admin")).thenReturn(admin);

        assertThat(stpInterface.getRoleList("u-admin", "login")).containsExactly("admin");
    }

    @Test
    void getRoleListReturnsEmptyWhenUserMissing() {
        StpInterfaceImpl stpInterface = new StpInterfaceImpl(userAccountMapper);
        when(userAccountMapper.selectById("ghost")).thenReturn(null);

        assertThat(stpInterface.getRoleList("ghost", "login")).isEmpty();
    }

    @Test
    void getRoleListReturnsEmptyWhenRoleNull() {
        StpInterfaceImpl stpInterface = new StpInterfaceImpl(userAccountMapper);
        UserAccount user = new UserAccount();
        user.setId("u-x");
        when(userAccountMapper.selectById("u-x")).thenReturn(user);

        assertThat(stpInterface.getRoleList("u-x", "login")).isEmpty();
    }

    @Test
    void getPermissionListIsAlwaysEmpty() {
        StpInterfaceImpl stpInterface = new StpInterfaceImpl(userAccountMapper);

        assertThat(stpInterface.getPermissionList("u-admin", "login")).isEmpty();
    }
}
