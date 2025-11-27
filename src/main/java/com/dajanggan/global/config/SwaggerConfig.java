/** 작성자 : 서샘이 */
package com.dajanggan.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI()
                .info(new Info()
                        .title("Dajanggan API 문서")
                        .description("PostgreSQL 모니터링 API")
                        .version("v1.0.0"));
    }
}
