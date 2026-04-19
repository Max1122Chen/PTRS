package com.travel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 相关通用配置。
 *
 * <p>
 * 这里主要配置跨域，支持前后端分离部署场景下的浏览器访问。
 * 如需限制来源，可将 {@code allowedOrigins} 替换为具体前端地址。
 * </p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer
{

    @Value("${app.media.base-path:data/media}")
    private String mediaBasePath;

    @Value("${app.media.url-prefix:/media}")
    private String mediaUrlPrefix;

    @Override
    public void addCorsMappings(CorsRegistry registry)
    {
        registry.addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);

        registry.addMapping(normalizeUrlPrefix(mediaUrlPrefix) + "/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "HEAD", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        String prefix = normalizeUrlPrefix(mediaUrlPrefix);
        String location = Paths.get(mediaBasePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(prefix + "/**")
            .addResourceLocations(location);
    }

    private String normalizeUrlPrefix(String prefix)
    {
        String out = (prefix == null || prefix.isBlank()) ? "/media" : prefix.trim();
        if (!out.startsWith("/"))
        {
            out = "/" + out;
        }
        while (out.endsWith("/"))
        {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}

