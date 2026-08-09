package com.serasaexperian.grainweighing.ingestion;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestionWebConfig {

    @Bean
    public FilterRegistrationBean<ScaleAuthFilter> scaleAuthFilter(ScaleCredentialsCache credentialsCache) {
        FilterRegistrationBean<ScaleAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ScaleAuthFilter(credentialsCache));
        registration.addUrlPatterns("/api/readings");
        return registration;
    }
}
