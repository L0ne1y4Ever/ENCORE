package com.encore.config;

import com.encore.entity.UserAccount;
import com.encore.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordHashMigrationRunnerTest {
    @Mock
    private UserAccountMapper userAccountMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void hashesPlaintextAndSkipsAlreadyHashed() {
        UserAccount plaintext = user("u-101", "123");
        UserAccount hashed = user("u-901", passwordEncoder.encode("123"));
        when(userAccountMapper.selectList(any())).thenReturn(List.of(plaintext, hashed));

        new PasswordHashMigrationRunner(userAccountMapper, passwordEncoder)
                .run(new DefaultApplicationArguments());

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper, times(1)).updateById(captor.capture());
        UserAccount migrated = captor.getValue();
        assertThat(migrated.getId()).isEqualTo("u-101");
        assertThat(migrated.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("123", migrated.getPassword())).isTrue();
    }

    @Test
    void doesNothingWhenNoPlaintextRemains() {
        UserAccount hashed = user("u-701", passwordEncoder.encode("admin"));
        when(userAccountMapper.selectList(any())).thenReturn(List.of(hashed));

        new PasswordHashMigrationRunner(userAccountMapper, passwordEncoder)
                .run(new DefaultApplicationArguments());

        verify(userAccountMapper, never()).updateById(any(UserAccount.class));
    }

    private UserAccount user(String id, String password) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(id);
        user.setPassword(password);
        user.setRole("user");
        user.setStatus("ACTIVE");
        return user;
    }
}
