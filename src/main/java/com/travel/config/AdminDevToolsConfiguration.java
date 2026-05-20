package com.travel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 管理端开发工具异步线程池。
 */
@Configuration
public class AdminDevToolsConfiguration
{

    @Bean(name = "adminOsmCollectExecutor")
    public ThreadPoolTaskExecutor adminOsmCollectExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("admin-osm-collect-");
        executor.initialize();
        return executor;
    }
}
