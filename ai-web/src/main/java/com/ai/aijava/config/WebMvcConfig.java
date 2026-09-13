package com.ai.aijava.config;

import com.ai.aijava.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置，注册 JWT 拦截器与异步请求线程池
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AsyncTaskExecutor mvcTaskExecutor;

    public WebMvcConfig(
            JwtInterceptor jwtInterceptor,
            @Qualifier("mvcTaskExecutor") AsyncTaskExecutor mvcTaskExecutor) {
        this.jwtInterceptor = jwtInterceptor;
        this.mvcTaskExecutor = mvcTaskExecutor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 拦截器匹配的是 context-path 之后的路径（如 /kb/create），
        // 不能带 /api 前缀；是否鉴权由 @RequireLogin 注解决定
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**");
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcTaskExecutor);
        // SSE 问答可能持续较久，默认 30s 会提前切断
        configurer.setDefaultTimeout(180_000);
    }
}
