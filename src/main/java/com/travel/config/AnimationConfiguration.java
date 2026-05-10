package com.travel.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

/**
 * 动画生成异步线程池与配置绑定。
 */
@Configuration
@EnableConfigurationProperties(AnimationProperties.class)
public class AnimationConfiguration
{

    @Bean
    public RestTemplate restTemplate()
    {
        return new RestTemplate();
    }

    @Bean(name = "animationTaskExecutor")
    public ThreadPoolTaskExecutor animationTaskExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("diary-animation-");
        executor.initialize();
        return executor;
    }
}
