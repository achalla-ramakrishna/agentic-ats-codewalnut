package com.codewalnut.ats.security;

import com.codewalnut.ats.client.GoogleCalendarClient;
import com.codewalnut.ats.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Calendar access tokens live in the staff member's session only: never stored in the
     * database, gone at sign-out. See ADR-0005.
     */
    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
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
                                "/api/v1/auth/dev-login", "/api/v1/public/**")
                        .permitAll()
                        // Only staff connect a calendar.
                        .requestMatchers("/oauth2/authorization/" + GoogleCalendarClient.REGISTRATION_ID,
                                "/oauth2/callback/**")
                        .hasAuthority(SessionType.STAFF.authority())
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // API callers get a 401, never a redirect to a login page.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
                // Nothing redirects back to a remembered request; sign-in always lands on successUrl.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
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
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        if (registrations != null) {
            log.info("Google sign-in: ENABLED; Google Calendar: {}",
                    registrations.findByRegistrationId(GoogleCalendarClient.REGISTRATION_ID) != null ? "ENABLED" : "DISABLED");
            OAuth2AuthorizationRequestResolver resolver = authorizationRequestResolver(registrations);
            // loginPage points at the SPA's /login route, so Spring doesn't generate its own
            // bare login page and failed sign-ins land on our page with ?error.
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService))
                    .defaultSuccessUrl(authProperties.successUrl(), true)
                    .failureUrl(authProperties.failureUrl()));
            // Connecting Google Calendar is an OAuth *client* grant on top of the existing session,
            // not a sign-in: the callback stores the token and leaves the signed-in user unchanged.
            http.oauth2Client(client -> client.authorizationCodeGrant(grant -> grant
                    .authorizationRequestResolver(resolver)));
        } else {
            log.info("Google sign-in: DISABLED (set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to enable)");
        }
        return http.build();
    }

    /**
     * Calendar connections are for staff only, and pre-select their own Google account. The
     * redirect filter runs before the URL rules, so non-staff are stopped here (no redirect to
     * Google); the URL rule then answers them with the usual 401/403.
     */
    private static OAuth2AuthorizationRequestResolver authorizationRequestResolver(ClientRegistrationRepository registrations) {
        DefaultOAuth2AuthorizationRequestResolver defaults =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return forCalendar(defaults.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return forCalendar(defaults.resolve(request, clientRegistrationId));
            }
        };
    }

    private static OAuth2AuthorizationRequest forCalendar(OAuth2AuthorizationRequest request) {
        if (request == null
                || !GoogleCalendarClient.REGISTRATION_ID.equals(request.getAttribute(OAuth2ParameterNames.REGISTRATION_ID))) {
            return request;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean staff = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> SessionType.STAFF.authority().equals(a.getAuthority()));
        if (!staff) {
            return null;
        }
        String email = auth.getName() != null && auth.getName().contains("@") ? auth.getName() : null;
        return OAuth2AuthorizationRequest.from(request)
                .additionalParameters(params -> {
                    if (email != null) {
                        params.put("login_hint", email);
                    }
                })
                .build();
    }
}
