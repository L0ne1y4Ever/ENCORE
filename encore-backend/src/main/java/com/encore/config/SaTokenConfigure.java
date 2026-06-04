package com.encore.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HTTP 层统一鉴权闸门。仅对受保护前缀显式校验，未匹配的公共路由（health、
 * auth/login|register|logout、shows、GET seats/areas、knife4j、/ws）直接放行。
 * service 层 ensureAdminRole/ensureCheckInRole 仍保留作纵深防御。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 放行 CORS 预检请求(OPTIONS)：浏览器在带自定义 token 头的跨域请求前会先发预检，
            // 预检不携带 token，若在此被 checkLogin/checkRole 拦截会返回 500，导致预检失败、
            // 真实请求被浏览器阻断，前端表现为 "Network Error"。
            SaRouter.match(SaHttpMethod.OPTIONS).stop();

            // 需要登录
            SaRouter.match("/api/orders/**").check(r -> StpUtil.checkLogin());
            SaRouter.match("/api/group-orders/**").check(r -> StpUtil.checkLogin());
            SaRouter.match("/api/auth/me").check(r -> StpUtil.checkLogin());
            SaRouter.match("/api/schedules/*/seats/lock")
                    .matchMethod("POST")
                    .check(r -> StpUtil.checkLogin());

            // 需要角色
            SaRouter.match("/api/admin/**").check(r -> StpUtil.checkRoleOr("admin", "sysadmin"));
            SaRouter.match("/api/checkin/**").check(r -> StpUtil.checkRoleOr("checker", "admin", "sysadmin"));
        })).addPathPatterns("/**");
    }
}
