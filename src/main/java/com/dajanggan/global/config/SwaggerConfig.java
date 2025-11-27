/** 작성자 : 서샘이 */
package com.dajanggan.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI()
                .info(new Info()
                        .title("Dajanggan API 문서")
                        .description("PostgreSQL 모니터링 시스템 API 명세서")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Dajanggan Team")
                                .email("support@dajanggan.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Server"),
                        new Server().url("https://api.dajanggan.com").description("Production Server")
                ));
    }
}
