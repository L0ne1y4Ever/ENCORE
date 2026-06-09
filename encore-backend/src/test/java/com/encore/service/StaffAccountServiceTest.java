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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void sysadminCreatesCheckerAccount() {
        StaffAccountService service = new StaffAccountService(userAccountMapper, passwordEncoder);
        when(userAccountMapper.selectById("u-sys")).thenReturn(user("u-sys", "sysadmin", "ACTIVE"));
        when(userAccountMapper.selectOne(any())).thenReturn(null);
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            var response = service.createStaffUser(new CreateStaffUserRequest(
                    " checker2 ", "Staff1234", " 检票员二号 ", "checker", null));

            assertThat(response.username()).isEqualTo("checker2");
            assertThat(response.role()).isEqualTo("checker");
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.editable()).isTrue();
        }

        verify(userAccountMapper).insert(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(passwordEncoder.matches("Staff1234", saved.getPassword())).isTrue();
        assertThat(saved.getDisplayName()).isEqualTo("检票员二号");
        assertThat(saved.getRole()).isEqualTo("checker");
    }

    @Test
    void adminCannotManageStaffAccounts() {
        StaffAccountService service = new StaffAccountService(userAccountMapper, passwordEncoder);
        when(userAccountMapper.selectById("u-admin")).thenReturn(user("u-admin", "admin", "ACTIVE"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-admin");
            assertThrows(BusinessException.class, service::listStaffUsers);
        }
    }

    @Test
    void duplicateUsernameIsRejected() {
        StaffAccountService service = new StaffAccountService(userAccountMapper, passwordEncoder);
        when(userAccountMapper.selectById("u-sys")).thenReturn(user("u-sys", "sysadmin", "ACTIVE"));
        when(userAccountMapper.selectOne(any())).thenReturn(user("u-checker", "checker", "ACTIVE"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            assertThrows(BusinessException.class, () -> service.createStaffUser(new CreateStaffUserRequest(
                    "checker", "Staff1234", "Checker", "checker", "ACTIVE")));
        }

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void staffCreateRejectsInvalidUsernameAndWeakPassword() {
        StaffAccountService service = new StaffAccountService(userAccountMapper, passwordEncoder);
        when(userAccountMapper.selectById("u-sys")).thenReturn(user("u-sys", "sysadmin", "ACTIVE"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");

            BusinessException usernameError = assertThrows(BusinessException.class, () -> service.createStaffUser(
                    new CreateStaffUserRequest("Checker Two", "Staff1234", "Checker", "checker", "ACTIVE")));
            assertThat(usernameError).hasMessage("账号需为 4 到 20 位小写字母、数字或下划线，并以小写字母开头");

            BusinessException passwordError = assertThrows(BusinessException.class, () -> service.createStaffUser(
                    new CreateStaffUserRequest("checker2", "pass1234", "Checker", "checker", "ACTIVE")));
            assertThat(passwordError).hasMessage("密码过于常见，请更换更安全的密码");
        }

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void sysadminAccountIsReadonlyInStaffPage() {
        StaffAccountService service = new StaffAccountService(userAccountMapper, passwordEncoder);
        UserAccount sysadmin = user("u-sys", "sysadmin", "ACTIVE");
        when(userAccountMapper.selectById("u-sys")).thenReturn(sysadmin);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");
            assertThrows(BusinessException.class, () -> service.updateStaffUser(
                    "u-sys", new UpdateStaffUserRequest("Root", "admin", "ACTIVE")));
            assertThrows(BusinessException.class, () -> service.resetPassword(
                    "u-sys", new ResetStaffPasswordRequest("Newpass123")));
        }

        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
    }

    @Test
    void sysadminCanDisableCheckerAndResetPassword() {
        StaffAccountService service = new StaffAccountService(userAccountMapper, passwordEncoder);
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
            var reset = service.resetPassword("u-checker", new ResetStaffPasswordRequest("Reset1234"));

            assertThat(disabled.status()).isEqualTo("INACTIVE");
            assertThat(reset.displayName()).isEqualTo("停用检票员");
        }

        assertThat(checker.getStatus()).isEqualTo("INACTIVE");
        assertThat(passwordEncoder.matches("Reset1234", checker.getPassword())).isTrue();
        verify(userAccountMapper, times(2)).updateById(checker);
    }

    @Test
    void staffResetPasswordRejectsWeakPassword() {
        StaffAccountService service = new StaffAccountService(userAccountMapper, passwordEncoder);
        UserAccount sysadmin = user("u-sys", "sysadmin", "ACTIVE");
        UserAccount checker = user("u-checker", "checker", "ACTIVE");
        when(userAccountMapper.selectById("u-sys")).thenReturn(sysadmin);
        when(userAccountMapper.selectById("u-checker")).thenReturn(checker);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u-sys");

            BusinessException error = assertThrows(BusinessException.class, () -> service.resetPassword(
                    "u-checker", new ResetStaffPasswordRequest("u-checker1234")));
            assertThat(error).hasMessage("密码不能包含账号");
        }

        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
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
