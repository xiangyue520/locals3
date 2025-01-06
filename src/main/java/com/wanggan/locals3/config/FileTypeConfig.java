package com.wanggan.locals3.config;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author wanggan
 * @desc:
 * @date 2024/12/26
 */
@Configuration
public class FileTypeConfig {
    @Bean
    public Tika tika(){
        return new Tika();
    }
}
