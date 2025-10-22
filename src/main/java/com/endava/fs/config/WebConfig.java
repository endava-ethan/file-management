package com.endava.fs.config;

import com.endava.fs.security.ApiKeyInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
public class WebConfig {

    private final SecurityProperties securityProperties;

    public WebConfig(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Bean
    public WebFilter apiKeyFilter() {
        return new ApiKeyInterceptor(securityProperties.apiKey());
    }
}
