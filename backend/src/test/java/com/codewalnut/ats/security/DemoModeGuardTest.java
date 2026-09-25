package com.codewalnut.ats.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewalnut.ats.config.AuthProperties;
import com.codewalnut.ats.config.DemoModeGuard;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoModeGuardTest {

    private static AuthProperties withCode(String code) {
        return new AuthProperties(List.of("codewalnut.com"), List.of(), true, code, null, null);
    }

    @Test
    void demoRefusesToStartWithoutAStrongAccessCode() {
        assertThatThrownBy(() -> new DemoModeGuard(withCode("")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DemoModeGuard(withCode("short")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new DemoModeGuard(withCode("long-enough-code")).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
