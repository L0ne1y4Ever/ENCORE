package com.encore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class CorsOriginProperties {
    private final String[] allowedOriginPatterns;

    public CorsOriginProperties(@Value("${encore.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String patterns) {
        this.allowedOriginPatterns = parsePatterns(patterns);
    }

    public String[] allowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    private String[] parsePatterns(String patterns) {
        String[] parsed = Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toArray(String[]::new);
        return parsed.length == 0
                ? new String[]{"http://localhost:*", "http://127.0.0.1:*"}
                : parsed;
    }
}
