package com.encore.service;

import com.encore.common.ErrorCode;
import com.encore.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

final class CredentialPolicy {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{3,19}$");
    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin", "sysadmin", "checker", "root", "system", "encore"
    );
    private static final Set<String> COMMON_WEAK_PASSWORDS = Set.of(
            "12345678",
            "123456789",
            "11111111",
            "password",
            "password123",
            "qwerty123",
            "admin123",
            "encore123",
            "abc12345",
            "pass1234"
    );

    private CredentialPolicy() {
    }

    static String normalizeUsername(String value) {
        String username = clean(value);
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号需为 4 到 20 位小写字母、数字或下划线，并以小写字母开头");
        }
        return username;
    }

    static String normalizePublicUsername(String value) {
        String username = normalizeUsername(value);
        if (RESERVED_USERNAMES.contains(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号为系统保留名称，请更换");
        }
        return username;
    }

    static String validatePassword(String value, String username) {
        if (value == null || value.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能为空");
        }
        if (value.length() < 8 || value.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度需为 8 到 64 个字符");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能包含空格或换行");
        }
        boolean hasLetter = value.chars().anyMatch(Character::isLetter);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码需同时包含字母和数字");
        }
        String lowerPassword = value.toLowerCase();
        if (StringUtils.hasText(username) && lowerPassword.contains(username.toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码不能包含账号");
        }
        if (COMMON_WEAK_PASSWORDS.contains(lowerPassword)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码过于常见，请更换更安全的密码");
        }
        return value;
    }

    static String normalizeDisplayName(String value) {
        String displayName = clean(value);
        if (displayName.length() < 2 || displayName.length() > 32) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称需为 2 到 32 个字符");
        }
        if (displayName.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称不能包含控制字符");
        }
        return displayName;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
