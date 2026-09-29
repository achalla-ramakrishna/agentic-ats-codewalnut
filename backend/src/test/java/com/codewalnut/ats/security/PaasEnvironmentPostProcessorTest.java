package com.codewalnut.ats.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewalnut.ats.config.PaasEnvironmentPostProcessor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PaasEnvironmentPostProcessorTest {

    private static StandardEnvironment env(Map<String, Object> vars) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", vars));
        new PaasEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        return environment;
    }

    @Test
    void railwayMysqlUrlBecomesTheDatasource() {
        var environment = env(Map.of("MYSQL_URL", "mysql://root:p%40ss:word@mysql.railway.internal:3306/railway"));

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(
                "jdbc:mysql://mysql.railway.internal:3306/railway?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("root");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p@ss:word");
    }

    @Test
    void explicitDbUrlWins() {
        var environment = env(Map.of("DB_URL", "jdbc:mysql://elsewhere/db", "MYSQL_URL", "mysql://u:p@h:3306/railway"));

        assertThat(environment.getPropertySources().contains("mysqlUrlDatasource")).isFalse();
    }

    @Test
    void nothingHappensWithoutMysqlUrl() {
        assertThat(env(Map.of()).getPropertySources().contains("mysqlUrlDatasource")).isFalse();
    }

    @Test
    void malformedUrlFailsClearly() {
        assertThatThrownBy(() -> env(Map.of("MYSQL_URL", "postgres://u:p@h/db")))
                .hasMessageContaining("mysql://");
    }

    @Test
    void shortGoogleVariablesConfigureGoogleSignIn() {
        var environment = env(Map.of("GOOGLE_CLIENT_ID", " abc.apps.googleusercontent.com ", "GOOGLE_CLIENT_SECRET", "s3cret"));

        assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-id"))
                .isEqualTo("abc.apps.googleusercontent.com");
        assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-secret"))
                .isEqualTo("s3cret");
    }

    @Test
    void googleNeedsBothIdAndSecret() {
        var environment = env(Map.of("GOOGLE_CLIENT_ID", "abc"));

        assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-id")).isNull();
    }

    @Test
    void fullSpringGooglePropertiesWin() {
        var environment = env(Map.of(
                "spring.security.oauth2.client.registration.google.client-id", "long-form",
                "GOOGLE_CLIENT_ID", "short-form", "GOOGLE_CLIENT_SECRET", "x"));

        assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-id"))
                .isEqualTo("long-form");
    }

    @Test
    void googleClientAlsoRegistersCalendarAccess() {
        var environment = env(Map.of("GOOGLE_CLIENT_ID", "abc", "GOOGLE_CLIENT_SECRET", "s3cret"));
        String prefix = "spring.security.oauth2.client.registration.google-calendar.";

        assertThat(environment.getProperty(prefix + "client-id")).isEqualTo("abc");
        assertThat(environment.getProperty(prefix + "provider")).isEqualTo("google");
        assertThat(environment.getProperty(prefix + "scope")).isEqualTo("https://www.googleapis.com/auth/calendar.events");
        assertThat(environment.getProperty(prefix + "redirect-uri")).isEqualTo("{baseUrl}/oauth2/callback/{registrationId}");
    }

    @Test
    void calendarCanBeSwitchedOff() {
        var environment = env(Map.of("GOOGLE_CLIENT_ID", "abc", "GOOGLE_CLIENT_SECRET", "s3cret",
                "ATS_GOOGLE_CALENDAR_ENABLED", "false"));

        assertThat(environment.getProperty("spring.security.oauth2.client.registration.google.client-id")).isEqualTo("abc");
        assertThat(environment.getProperty("spring.security.oauth2.client.registration.google-calendar.client-id")).isNull();
    }
}
