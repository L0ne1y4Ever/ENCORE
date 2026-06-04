package com.encore.service;

import cn.dev33.satoken.stp.StpUtil;
import com.encore.dto.CreateStaffUserRequest;
import com.encore.dto.ResetStaffPasswordRequest;
import com.encore.dto.UpdateStaffUserRequest;
import com.encore.entity.UserAccount;
import com.encore.exception.BusinessException;
import com.encore.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAccountServiceTest {
    @Mock
    private UserAccountMapper userAccountMapper;

    @Test
    void sysadminCreatesCheckerAccount() {
        StaffAccountService service = new StaffAccountService(userAccountMapper);
        when(userAccountMapper.selectById("u-sys")).thenReturn(user("u-sys", "sysadmin", "ACTIVE"));
        when(userAccountMapper.selectOne(any())).thenReturn(null);
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            var response = service.createStaffUser(new CreateStaffUserRequest(
                    " checker2 ", " 123456 ", " 检票员二号 ", "checker", null));

            assertThat(response.username()).isEqualTo("checker2");
            assertThat(response.role()).isEqualTo("checker");
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.editable()).isTrue();
        }

        verify(userAccountMapper).insert(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("123456");
        assertThat(saved.getDisplayName()).isEqualTo("检票员二号");
        assertThat(saved.getRole()).isEqualTo("checker");
    }

    @Test
    void adminCannotManageStaffAccounts() {
        StaffAccountService service = new StaffAccountService(userAccountMapper);
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin", "ACTIVE"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThrows(BusinessException.class, service::listStaffUsers);
        }
    }

    @Test
    void duplicateUsernameIsRejected() {
        StaffAccountService service = new StaffAccountService(userAccountMapper);
        when(userAccountMapper.selectById("u-sys")).thenReturn(user("u-sys", "sysadmin", "ACTIVE"));
        when(userAccountMapper.selectOne(any())).thenReturn(user("u-checker", "checker", "ACTIVE"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            assertThrows(BusinessException.class, () -> service.createStaffUser(new CreateStaffUserRequest(
                    "checker", "123456", "Checker", "checker", "ACTIVE")));
        }

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void sysadminAccountIsReadonlyInStaffPage() {
        StaffAccountService service = new StaffAccountService(userAccountMapper);
        UserAccount sysadmin = user("u-sys", "sysadmin", "ACTIVE");
        when(userAccountMapper.selectById("u-sys")).thenReturn(sysadmin);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            assertThrows(BusinessException.class, () -> service.updateStaffUser(
                    "u-sys", new UpdateStaffUserRequest("Root", "admin", "ACTIVE")));
            assertThrows(BusinessException.class, () -> service.resetPassword(
                    "u-sys", new ResetStaffPasswordRequest("newpass")));
        }

        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
    }

    @Test
    void sysadminCanDisableCheckerAndResetPassword() {
        StaffAccountService service = new StaffAccountService(userAccountMapper);
        UserAccount sysadmin = user("u-sys", "sysadmin", "ACTIVE");
        UserAccount checker = user("u-checker", "checker", "ACTIVE");
        checker.setDisplayName("Checker");
        checker.setPassword("123");
        when(userAccountMapper.selectById("u-sys")).thenReturn(sysadmin);
        when(userAccountMapper.selectById("u-checker")).thenReturn(checker);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            var disabled = service.updateStaffUser(
                    "u-checker", new UpdateStaffUserRequest("停用检票员", "checker", "INACTIVE"));
            var reset = service.resetPassword("u-checker", new ResetStaffPasswordRequest("456789"));

            assertThat(disabled.status()).isEqualTo("INACTIVE");
            assertThat(reset.displayName()).isEqualTo("停用检票员");
        }

        assertThat(checker.getStatus()).isEqualTo("INACTIVE");
        assertThat(checker.getPassword()).isEqualTo("456789");
        verify(userAccountMapper, times(2)).updateById(checker);
    }

    private UserAccount user(String id, String role, String status) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(id);
        user.setPassword("123");
        user.setRole(role);
        user.setDisplayName(id);
        user.setStatus(status);
        return user;
    }
}
