package com.codewalnut.ats.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewalnut.ats.config.RailwayMysqlUrlPostProcessor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class RailwayMysqlUrlPostProcessorTest {

    private static StandardEnvironment env(Map<String, Object> vars) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", vars));
        new RailwayMysqlUrlPostProcessor().postProcessEnvironment(environment, null);
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
}
