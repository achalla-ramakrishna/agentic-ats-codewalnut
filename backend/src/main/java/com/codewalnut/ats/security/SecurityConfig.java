package com.codewalnut.ats.security;

import com.codewalnut.ats.config.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            GoogleOidcUserService googleOidcUserService,
            SecurityContextRepository securityContextRepository,
            AuthProperties authProperties) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/api/v1/auth/config", "/api/v1/auth/session",
                                "/api/v1/auth/dev-login")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // API callers get a 401, never a redirect to a login page.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        // Google sign-in is only wired when a client registration is configured
        // (GOOGLE_CLIENT_ID + GOOGLE_CLIENT_SECRET, see PaasEnvironmentPostProcessor).
        if (clientRegistrations.getIfAvailable() != null) {
            log.info("Google sign-in: ENABLED");
            // loginPage points at the SPA's /login route, so Spring doesn't generate its own
            // bare login page and failed sign-ins land on our page with ?error.
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService))
                    .defaultSuccessUrl(authProperties.successUrl(), true)
                    .failureUrl(authProperties.failureUrl()));
        } else {
            log.info("Google sign-in: DISABLED (set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to enable)");
        }
        return http.build();
    }
}
