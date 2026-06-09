package com.encore.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.encore.dto.LoginRequest;
import com.encore.dto.LoginResponse;
import com.encore.dto.RegisterRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserAccountMapper userAccountMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void registerCreatesUserAndReturnsLoginToken() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder);
        SaTokenInfo tokenInfo = tokenInfo();
        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);

        when(userAccountMapper.selectOne(any())).thenReturn(null);
        when(userAccountMapper.selectById(anyString())).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenInfo).thenReturn(tokenInfo);

            LoginResponse response = service.register(new RegisterRequest(" test_user ", "Secure123", " Tester "));

            assertThat(response.tokenName()).isEqualTo("satoken");
            assertThat(response.tokenValue()).isEqualTo("token-1");
            assertThat(response.user().username()).isEqualTo("test_user");
            assertThat(response.user().displayName()).isEqualTo("Tester");
            stp.verify(() -> StpUtil.login(response.user().id()));
        }

        verify(userAccountMapper).insert(userCaptor.capture());
        UserAccount saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("test_user");
        assertThat(passwordEncoder.matches("Secure123", saved.getPassword())).isTrue();
        assertThat(saved.getRole()).isEqualTo("user");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder);
        when(userAccountMapper.selectOne(any())).thenReturn(user("u-1", "test", "user", "ACTIVE"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("test", "Secure123", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已存在");

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void registerRejectsInvalidUsernameAndReservedName() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder);

        assertThatThrownBy(() -> service.register(new RegisterRequest("1user", "Secure123", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号需为 4 到 20 位小写字母、数字或下划线，并以小写字母开头");
        assertThatThrownBy(() -> service.register(new RegisterRequest("测试账号", "Secure123", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号需为 4 到 20 位小写字母、数字或下划线，并以小写字母开头");
        assertThatThrownBy(() -> service.register(new RegisterRequest("admin", "Secure123", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号为系统保留名称，请更换");

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void registerRejectsWeakPassword() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder);

        assertThatThrownBy(() -> service.register(new RegisterRequest("valid_user", "short1", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码长度需为 8 到 64 个字符");
        assertThatThrownBy(() -> service.register(new RegisterRequest("valid_user", "password", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码需同时包含字母和数字");
        assertThatThrownBy(() -> service.register(new RegisterRequest("valid_user", "12345678", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码需同时包含字母和数字");
        assertThatThrownBy(() -> service.register(new RegisterRequest("valid_user", "abc 12345", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码不能包含空格或换行");
        assertThatThrownBy(() -> service.register(new RegisterRequest("valid_user", "valid_user123", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码不能包含账号");
        assertThatThrownBy(() -> service.register(new RegisterRequest("valid_user", "pass1234", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("密码过于常见，请更换更安全的密码");

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void registerRejectsInvalidDisplayName() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder);

        assertThatThrownBy(() -> service.register(new RegisterRequest("valid_user", "Secure123", "A")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("昵称需为 2 到 32 个字符");
        assertThatThrownBy(() -> service.register(new RegisterRequest("valid_user", "Secure123", "Bad\nName")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("昵称不能包含控制字符");

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void loginStillReturnsExistingUserToken() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder);
        UserAccount user = user("u-101", "user", "user", "ACTIVE");
        user.setPassword(passwordEncoder.encode("123"));
        when(userAccountMapper.selectOne(any())).thenReturn(user);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenInfo).thenReturn(tokenInfo());

            LoginResponse response = service.login(new LoginRequest("user", "123"));

            assertThat(response.user().id()).isEqualTo("u-101");
            assertThat(response.tokenValue()).isEqualTo("token-1");
            stp.verify(() -> StpUtil.login("u-101"));
        }
    }

    @Test
    void loginRejectsInactiveStaffAccount() {
        AuthService service = new AuthService(userAccountMapper, passwordEncoder);
        UserAccount checker = user("u-checker", "checker2", "checker", "INACTIVE");
        checker.setPassword("123");
        when(userAccountMapper.selectOne(any())).thenReturn(checker);

        assertThatThrownBy(() -> service.login(new LoginRequest("checker2", "123")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
    }

    private SaTokenInfo tokenInfo() {
        SaTokenInfo tokenInfo = new SaTokenInfo();
        tokenInfo.setTokenName("satoken");
        tokenInfo.setTokenValue("token-1");
        return tokenInfo;
    }

    private UserAccount user(String id, String username, String role, String status) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("123456");
        user.setRole(role);
        user.setDisplayName("Tester");
        user.setStatus(status);
        return user;
    }
}
