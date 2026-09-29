package com.codewalnut.ats.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Maps short, PaaS-friendly environment variables onto Spring properties, so a deployment needs
 * only a few obvious variables:
 *
 * <ul>
 *   <li>{@code MYSQL_URL} (e.g. Railway's {@code mysql://user:pass@host:port/db}) → datasource
 *       URL, username and password — unless {@code DB_URL} is set, which always wins.
 *   <li>{@code GOOGLE_CLIENT_ID} + {@code GOOGLE_CLIENT_SECRET} → the Google sign-in client
 *       registration — unless the full {@code spring.security.oauth2.client.registration.google.*}
 *       properties are set, which win.
 *   <li>The same Google client also gets a second registration, {@code google-calendar}, used
 *       only when staff connect Google Calendar to schedule interviews (scope
 *       {@code calendar.events}). Set {@code ATS_GOOGLE_CALENDAR_ENABLED=false} to leave it out.
 * </ul>
 */
public class PaasEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "mysqlUrlDatasource";
    static final String GOOGLE_SOURCE_NAME = "googleClientShortNames";
    private static final String GOOGLE_PREFIX = "spring.security.oauth2.client.registration.google.";
    private static final String CALENDAR_PREFIX = "spring.security.oauth2.client.registration.google-calendar.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        applyMysqlUrl(environment);
        applyGoogleClient(environment);
    }

    private static void applyMysqlUrl(ConfigurableEnvironment environment) {
        if (StringUtils.hasText(environment.getProperty("DB_URL"))) {
            return;
        }
        String mysqlUrl = environment.getProperty("MYSQL_URL");
        if (!StringUtils.hasText(mysqlUrl)) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, toDatasourceProperties(mysqlUrl)));
    }

    private static void applyGoogleClient(ConfigurableEnvironment environment) {
        Map<String, Object> props = new HashMap<>();
        String clientId = environment.getProperty(GOOGLE_PREFIX + "client-id");
        String clientSecret = environment.getProperty(GOOGLE_PREFIX + "client-secret");
        if (!StringUtils.hasText(clientId)) {
            clientId = environment.getProperty("GOOGLE_CLIENT_ID");
            clientSecret = environment.getProperty("GOOGLE_CLIENT_SECRET");
            if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
                return;
            }
            clientId = clientId.trim();
            clientSecret = clientSecret.trim();
            props.put(GOOGLE_PREFIX + "client-id", clientId);
            props.put(GOOGLE_PREFIX + "client-secret", clientSecret);
        }
        boolean calendarEnabled = !"false".equalsIgnoreCase(environment.getProperty("ATS_GOOGLE_CALENDAR_ENABLED"));
        if (calendarEnabled && StringUtils.hasText(clientSecret)
                && !StringUtils.hasText(environment.getProperty(CALENDAR_PREFIX + "client-id"))) {
            props.put(CALENDAR_PREFIX + "provider", "google");
            props.put(CALENDAR_PREFIX + "client-id", clientId);
            props.put(CALENDAR_PREFIX + "client-secret", clientSecret);
            props.put(CALENDAR_PREFIX + "client-name", "Google Calendar");
            props.put(CALENDAR_PREFIX + "authorization-grant-type", "authorization_code");
            props.put(CALENDAR_PREFIX + "redirect-uri", "{baseUrl}/oauth2/callback/{registrationId}");
            props.put(CALENDAR_PREFIX + "scope", "https://www.googleapis.com/auth/calendar.events");
        }
        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(GOOGLE_SOURCE_NAME, props));
        }
    }

    static Map<String, Object> toDatasourceProperties(String mysqlUrl) {
        URI uri = URI.create(mysqlUrl.trim());
        if (!"mysql".equals(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("MYSQL_URL must look like mysql://user:password@host:port/database");
        }
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (database.isEmpty()) {
            throw new IllegalStateException("MYSQL_URL must include a database name");
        }
        int port = uri.getPort() == -1 ? 3306 : uri.getPort();
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", "jdbc:mysql://" + uri.getHost() + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8");
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
            props.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
            if (colon >= 0) {
                props.put("spring.datasource.password",
                        URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8));
            }
        }
        return props;
    }
}
