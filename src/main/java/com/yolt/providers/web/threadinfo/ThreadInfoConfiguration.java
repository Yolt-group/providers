package com.yolt.providers.web.threadinfo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ThreadInfoConfiguration {

    @Bean
    public ThreadInfoService threadInfoService(){
        return new ThreadInfoService(Thread::getAllStackTraces);
    }
}
