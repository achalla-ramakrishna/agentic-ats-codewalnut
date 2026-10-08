package com.codewalnut.ats.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentStorageCleanupConfig {
    @Bean
    public Clock documentStorageCleanupClock() { return Clock.systemUTC(); }
}
