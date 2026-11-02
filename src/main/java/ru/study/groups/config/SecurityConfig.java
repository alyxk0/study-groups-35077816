package ru.study.groups.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.study.groups.service.AccountService;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(AccountService accounts) {
        return username -> {
            var user = accounts.find(username);
            if (user == null) throw new UsernameNotFoundException("Пользователь не найден");
            return User.withUsername(user.getUsername()).password(user.getPasswordHash()).roles("USER").build();
        };
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
            .requestMatchers("/", "/index.html", "/app.js", "/style.css", "/favicon.svg", "/error", "/api/session", "/api/register", "/api/login").permitAll()
            .anyRequest().authenticated());
        http.formLogin(form -> form.loginProcessingUrl("/api/login")
            .successHandler((request, response, authentication) -> {
                response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"ok\":true}");
            })
            .failureHandler((request, response, ex) -> {
                response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Неверный логин или пароль\"}");
            }));
        http.logout(logout -> logout.logoutUrl("/api/logout").logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Войдите в систему\"}");
            })
            .accessDeniedHandler((request, response, ex) -> {
                response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Сессия устарела. Обновите страницу\"}");
            }));
        return http.build();
    }
}
