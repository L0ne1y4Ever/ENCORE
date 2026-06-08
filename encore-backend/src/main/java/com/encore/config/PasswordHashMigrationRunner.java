package com.encore.config;

import com.encore.entity.UserAccount;
import com.encore.mapper.UserAccountMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 启动时将历史明文密码升级为 BCrypt 哈希（幂等）。
 *
 * <p>背景：种子数据（V1/V2 迁移、db/init）以文档化的演示密码明文 '123' 写入，且旧库里也都是明文。
 * 本运行器在 Flyway 迁移之后、应用对外服务时将这些明文一次性哈希入库；已是 BCrypt（'$2' 前缀）的记录跳过，
 * 因此可安全地在每次启动执行。配合 AuthService/StaffAccountService 的写时哈希，库中不再长期保存明文密码。
 * 登录仍用 {@code passwordEncoder.matches(原文, 哈希)}，演示账号继续用 '123' 登录。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PasswordHashMigrationRunner implements ApplicationRunner {
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public PasswordHashMigrationRunner(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<UserAccount> users = userAccountMapper.selectList(null);
        for (UserAccount user : users) {
            String stored = user.getPassword();
            if (StringUtils.hasText(stored) && !isBcryptHash(stored)) {
                user.setPassword(passwordEncoder.encode(stored));
                user.setUpdatedAt(LocalDateTime.now());
                userAccountMapper.updateById(user);
            }
        }
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2");
    }
}
