package com.tracefault.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TraceFault — Duplicate Payment Detection and Recovery API")
                        .description("Reliability engineering prototype API for reproducing, investigating, correcting, and verifying duplicate payment processing caused by lost HTTP responses and client retries.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TraceFault Reliability Engineering Team")
                                .email("reliability@tracefault.internal"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .tags(List.of(
                        new Tag().name("Payments").description("Core payment operations including submission, transaction query, and financial auditing statistics"),
                        new Tag().name("Demonstration Control").description("Controlled failure injection, test database reset, and runtime idempotency mode management")
                ));
    }
}
