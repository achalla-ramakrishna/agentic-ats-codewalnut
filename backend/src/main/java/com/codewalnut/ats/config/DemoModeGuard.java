package com.codewalnut.ats.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The demo profile exposes a password-less login on a public URL, so it must never start
 * without a strong shared access code, and never together with the dev profile.
 */
@Component
@Profile("demo")
public class DemoModeGuard implements InitializingBean {

    static final int MIN_CODE_LENGTH = 12;

    private final AuthProperties authProperties;

    public DemoModeGuard(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (authProperties.demoAccessCode().length() < MIN_CODE_LENGTH) {
            throw new IllegalStateException("Demo profile requires ATS_DEMO_ACCESS_CODE with at least "
                    + MIN_CODE_LENGTH + " characters");
        }
    }
}
