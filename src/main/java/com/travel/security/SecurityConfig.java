package com.travel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 安全配置。
 *
 * <p>
 * 采用 JWT 无状态认证模式：
 * <ul>
 *     <li>登录、注册等开放接口允许匿名访问</li>
 *     <li>其他以 /api/** 开头的接口默认需要认证</li>
 *     <li>关闭 CSRF 与 Session，适配前后端分离</li>
 * </ul>
 * 具体的 JWT 过滤器会在后续实现认证模块时补充。
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig
{

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.security.auth-enabled:true}")
    private boolean authEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter)
    {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 安全过滤链配置。
     *
     * @param http HttpSecurity
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
    {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!authEnabled)
        {
            log.warn("Security auth is disabled by config: app.security.auth-enabled=false");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            // Dev mode keeps token parsing so APIs depending on current user can still resolve userId.
            http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/ai/chat").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/recommendation/scenic-search").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tags").permitAll()
                .requestMatchers("/api/**").authenticated()
            )
            .httpBasic(Customizer.withDefaults());

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密码编码器，采用 BCrypt 算法。
     *
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}

