package com.quietterminal.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.quietterminal.ledger.enums.Permission;
import com.quietterminal.ledger.security.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden\"}");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean devProfile = environment.matchesProfiles("dev");

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/health", "/auth/login", "/auth/recovery-login", "/branding", "/error")
                            .permitAll()
                            .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**", "/*.css", "/*.js")
                            .permitAll()
                            .requestMatchers(HttpMethod.POST, "/integrations/webhooks/**")
                            .permitAll();
                    if (devProfile) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }

                    auth.requestMatchers(HttpMethod.GET, "/tasks/**").hasAuthority(Permission.TASKS_READ.name())
                            .requestMatchers(HttpMethod.POST, "/tasks/**").hasAuthority(Permission.TASKS_WRITE.name())
                            .requestMatchers(HttpMethod.PATCH, "/tasks/**")
                            .hasAuthority(Permission.TASKS_WRITE.name())
                            .requestMatchers(HttpMethod.DELETE, "/tasks/**")
                            .hasAuthority(Permission.TASKS_WRITE.name())

                            .requestMatchers(HttpMethod.GET, "/budget/**").hasAuthority(Permission.BUDGET_READ.name())
                            .requestMatchers(HttpMethod.POST, "/budget/**")
                            .hasAuthority(Permission.BUDGET_WRITE.name())
                            .requestMatchers(HttpMethod.PATCH, "/budget/**")
                            .hasAuthority(Permission.BUDGET_WRITE.name())
                            .requestMatchers(HttpMethod.DELETE, "/budget/**")
                            .hasAuthority(Permission.BUDGET_WRITE.name())

                            .requestMatchers(HttpMethod.GET, "/wiki/**").hasAuthority(Permission.WIKI_READ.name())
                            .requestMatchers(HttpMethod.POST, "/wiki/**").hasAuthority(Permission.WIKI_WRITE.name())
                            .requestMatchers(HttpMethod.PATCH, "/wiki/**").hasAuthority(Permission.WIKI_WRITE.name())
                            .requestMatchers(HttpMethod.DELETE, "/wiki/**")
                            .hasAuthority(Permission.WIKI_WRITE.name())

                            .requestMatchers(HttpMethod.GET, "/uploads/**").hasAuthority(Permission.FILES_READ.name())
                            .requestMatchers(HttpMethod.POST, "/uploads/**")
                            .hasAuthority(Permission.FILES_WRITE.name())
                            .requestMatchers(HttpMethod.DELETE, "/uploads/**")
                            .hasAuthority(Permission.FILES_WRITE.name())

                            .requestMatchers(HttpMethod.GET, "/email/**").hasAuthority(Permission.MAIL_READ.name())
                            .requestMatchers(HttpMethod.GET, "/search/**").hasAuthority(Permission.SEARCH_READ.name())
                            .requestMatchers(HttpMethod.GET, "/repos/**").hasAuthority(Permission.REPOS_READ.name())

                            .requestMatchers(HttpMethod.GET, "/users").authenticated()
                            .requestMatchers(HttpMethod.POST, "/users/**")
                            .hasAuthority(Permission.USERS_MANAGE.name())
                            .requestMatchers(HttpMethod.PATCH, "/users/**")
                            .hasAuthority(Permission.USERS_MANAGE.name())
                            .requestMatchers(HttpMethod.DELETE, "/users/**")
                            .hasAuthority(Permission.USERS_MANAGE.name())

                            .requestMatchers(HttpMethod.GET, "/roles").authenticated()
                            .requestMatchers(HttpMethod.POST, "/roles/**")
                            .hasAuthority(Permission.ROLES_MANAGE.name())
                            .requestMatchers(HttpMethod.PATCH, "/roles/**")
                            .hasAuthority(Permission.ROLES_MANAGE.name())
                            .requestMatchers(HttpMethod.DELETE, "/roles/**")
                            .hasAuthority(Permission.ROLES_MANAGE.name())

                            .requestMatchers("/auth/sessions/**", "/auth/recovery-codes/**", "/admin/**")
                            .authenticated();

                    auth.anyRequest().authenticated();
                })
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
