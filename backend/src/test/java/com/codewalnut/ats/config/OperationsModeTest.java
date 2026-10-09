package com.codewalnut.ats.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codewalnut.ats.security.MaintenanceFilter;
import com.codewalnut.ats.service.AiWorkQueue;
import com.codewalnut.ats.service.CodeRunService;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class OperationsModeTest {
    @Test
    void DEPLOY_03_openHttpWithPausedWorkersWarnsOperator(org.springframework.boot.test.system.CapturedOutput output) {
        OperationsMode mode = new OperationsMode(false, false, false, new MockEnvironment());
        assertThat(mode.maintenance()).isFalse();
        assertThat(mode.backgroundWorkEnabled()).isFalse();
        assertThat(output.getOut()).contains("HTTP is open while background work is disabled");
    }

    @Test
    void DEPLOY_01_normalModeAllowsTrafficAndWork() throws Exception {
        OperationsMode mode = new OperationsMode(false, true, false, new MockEnvironment());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new MaintenanceFilter(mode).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        Runnable task = mock(Runnable.class);
        new AiWorkQueue(false, 1, mode).submit(task);
        verify(task).run();
    }

    @Test
    void DEPLOY_01_freezeRejectsReadsWritesAuthAndWebhooksButAllowsHealth() throws Exception {
        OperationsMode mode = new OperationsMode(true, true, false, new MockEnvironment());
        assertThat(mode.backgroundWorkEnabled()).isFalse();
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
            for (String path : new String[]{"/api/v1/tests", "/oauth2/authorization/google", "/webhooks/whatsapp", "/"}) {
                FilterChain chain = mock(FilterChain.class);
                MockHttpServletResponse response = new MockHttpServletResponse();
                new MaintenanceFilter(mode).doFilter(new MockHttpServletRequest(method, path), response, chain);
                assertThat(response.getStatus()).isEqualTo(503);
                assertThat(response.getContentAsString()).isEqualTo(
                        "{\"error\":\"Scheduled maintenance. Please try again shortly.\"}");
                assertThat(response.getHeader("Retry-After")).isEqualTo("300");
                assertThat(response.getHeader("Cache-Control")).contains("no-store");
                verifyNoInteractions(chain);
            }
        }
        FilterChain health = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new MaintenanceFilter(mode).doFilter(request, response, health);
        verify(health).doFilter(request, response);
    }

    @Test
    void DEPLOY_02_rehearsalOverridesAccidentalWorkSettingsAndRejectsPasswordlessProfiles() {
        OperationsMode mode = new OperationsMode(false, true, true, new MockEnvironment());
        assertThat(mode.maintenance()).isTrue();
        assertThat(mode.backgroundWorkEnabled()).isFalse();
        Runnable task = mock(Runnable.class);
        AiWorkQueue queue = new AiWorkQueue(false, 1, mode);
        queue.submit(task);
        queue.afterCommit(task);
        verifyNoInteractions(task);
        CodeRunService code = new CodeRunService(null, null, null, null, null, false, 1, mode);
        code.resumePending();
        code.submit(UUID.randomUUID());
        code.grade(UUID.randomUUID());
        for (String profile : new String[]{"dev", "demo"}) {
            assertThatThrownBy(() -> new OperationsMode(false, true, true,
                    new MockEnvironment().withProperty("spring.profiles.active", profile)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
