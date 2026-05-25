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

    @Test
    void registerCreatesUserAndReturnsLoginToken() {
        AuthService service = new AuthService(userAccountMapper);
        SaTokenInfo tokenInfo = tokenInfo();
        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);

        when(userAccountMapper.selectOne(any())).thenReturn(null);
        when(userAccountMapper.selectById(anyString())).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenInfo).thenReturn(tokenInfo);

            LoginResponse response = service.register(new RegisterRequest(" test ", "123456", " Tester "));

            assertThat(response.tokenName()).isEqualTo("satoken");
            assertThat(response.tokenValue()).isEqualTo("token-1");
            assertThat(response.user().username()).isEqualTo("test");
            assertThat(response.user().displayName()).isEqualTo("Tester");
            stp.verify(() -> StpUtil.login(response.user().id()));
        }

        verify(userAccountMapper).insert(userCaptor.capture());
        UserAccount saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("test");
        assertThat(saved.getPassword()).isEqualTo("123456");
        assertThat(saved.getRole()).isEqualTo("user");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        AuthService service = new AuthService(userAccountMapper);
        when(userAccountMapper.selectOne(any())).thenReturn(user("u-1", "test", "user", "ACTIVE"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("test", "123456", "Tester")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已存在");

        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void loginStillReturnsExistingUserToken() {
        AuthService service = new AuthService(userAccountMapper);
        UserAccount user = user("u-101", "user", "user", "ACTIVE");
        user.setPassword("123");
        when(userAccountMapper.selectOne(any())).thenReturn(user);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getTokenInfo).thenReturn(tokenInfo());

            LoginResponse response = service.login(new LoginRequest("user", "123"));

            assertThat(response.user().id()).isEqualTo("u-101");
            assertThat(response.tokenValue()).isEqualTo("token-1");
            stp.verify(() -> StpUtil.login("u-101"));
        }
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
