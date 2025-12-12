package edc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EDC POC Management API")
                        .description("API for managing dataspace participants. " +
                                   "Acts as a proxy to provisioning APIs and manages the complete lifecycle " +
                                   "of participants including credentials and operations.")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("EDC Team")
                                .email("support@mail.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("https://localhost:8082/api/v1")
                                .description("Production environment"),
                        new Server()
                                .url("http://localhost:8080/v1")
                                .description("Development environment")
                ));
    }
}
