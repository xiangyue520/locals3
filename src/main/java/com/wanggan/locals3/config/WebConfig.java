package com.wanggan.locals3.config;

import com.wanggan.locals3.constant.S3Constant;
import com.wanggan.locals3.inteceptor.S3Interceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Resource
    S3Interceptor s3Interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(s3Interceptor).addPathPatterns(S3Constant.ENDPOINT + "/**");
    }
}
