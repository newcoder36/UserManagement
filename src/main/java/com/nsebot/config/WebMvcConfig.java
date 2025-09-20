package com.nsebot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration to fix static resource handler ordering
 * 
 * The issue was that Spring Boot's default ResourceHttpRequestHandler 
 * was taking precedence over @RequestMapping annotations.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Set resource handlers to LOWEST_PRECEDENCE so they don't interfere 
        // with @RequestMapping controllers
        registry
            .addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/")
            .setCachePeriod(0)
            .resourceChain(false);
        
        registry
            .addResourceHandler("/webjars/**")  
            .addResourceLocations("classpath:/META-INF/resources/webjars/")
            .setCachePeriod(0)
            .resourceChain(false);
            
        // Explicitly set the order for the entire registry
        registry.setOrder(Ordered.LOWEST_PRECEDENCE);
    }
}