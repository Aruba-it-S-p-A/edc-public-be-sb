package edc.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfigurationProvider {

    @Bean
    public CorsGlobalSource corsGlobalSource() {
        return new CorsGlobalSource();
    }

}
