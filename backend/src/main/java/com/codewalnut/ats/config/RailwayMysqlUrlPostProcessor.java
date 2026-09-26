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
 * Lets a PaaS database URL configure the datasource directly. If {@code DB_URL} is not set and
 * {@code MYSQL_URL} is (e.g. Railway's {@code mysql://user:pass@host:port/db}), it is turned into
 * the JDBC URL, username and password. An explicit {@code DB_URL} always wins.
 */
public class RailwayMysqlUrlPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "mysqlUrlDatasource";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty("DB_URL"))) {
            return;
        }
        String mysqlUrl = environment.getProperty("MYSQL_URL");
        if (!StringUtils.hasText(mysqlUrl)) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, toDatasourceProperties(mysqlUrl)));
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
