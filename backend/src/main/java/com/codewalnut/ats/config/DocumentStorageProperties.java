package com.codewalnut.ats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ats.document-storage")
public record DocumentStorageProperties(boolean enabled, String bridgeUrl, String bridgeSecret,
        boolean operationsEnabled) {}
