package com.agricore.identity.infrastructure.configuration;

import com.agricore.identity.infrastructure.security.JwtAuthenticationFilter;
import com.agricore.identity.infrastructure.security.RefreshCookieSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agricore.common.api.ApiError;
import com.agricore.identity.domain.exception.IdentityException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final RequestMatcher BROWSER_AUTH_POST =
            new AntPathRequestMatcher("/api/v1/auth/web/**", HttpMethod.POST.name());

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;
    private final RefreshCookieSupport refreshCookieSupport;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper,
            SecurityProperties securityProperties,
            RefreshCookieSupport refreshCookieSupport
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
        this.refreshCookieSupport = refreshCookieSupport;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(securityProperties.bcryptStrength());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(browserAuthCsrfTokenRepository())
                        .requireCsrfProtectionMatcher(this::requiresBrowserAuthCsrfToken)
                        .addObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            @Override
                            public <O extends CsrfFilter> O postProcess(O filter) {
                                filter.setAccessDeniedHandler(SecurityConfig.this::writeAccessDenied);
                                return filter;
                            }
                        })
                )
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/.well-known/jwks.json",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("SYSTEM_ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, 401, "UNAUTHORIZED", "Authentication required", request.getRequestURI()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeAccessDenied(request, response, accessDeniedException))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CookieCsrfTokenRepository browserAuthCsrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookiePath("/api/v1/auth/web");
        return repository;
    }

    private boolean requiresBrowserAuthCsrfToken(jakarta.servlet.http.HttpServletRequest request) {
        return BROWSER_AUTH_POST.matches(request) && !refreshCookieSupport.isAllowedBrowserOrigin(request);
    }

    private void writeAccessDenied(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws java.io.IOException {
        if (accessDeniedException instanceof CsrfException && BROWSER_AUTH_POST.matches(request)) {
            try {
                refreshCookieSupport.requireAllowedBrowserOrigin(request);
            } catch (IdentityException originException) {
                writeError(
                        response,
                        originException.getHttpStatus(),
                        originException.getCode(),
                        originException.getMessage(),
                        request.getRequestURI()
                );
                return;
            }
        }
        writeError(response, 403, "FORBIDDEN", "Access denied", request.getRequestURI());
    }

    private void writeError(HttpServletResponse response, int status, String code, String message, String path)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(status, status == 401 ? "Unauthorized" : "Forbidden", code, message, path, null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
