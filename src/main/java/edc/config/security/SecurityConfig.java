package edc.config.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationProvider corsConfigurationProvider,
                                                   CustomAuthenticationManager authenticationManager) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Disabilita CSRF
                .cors(cors -> cors.configurationSource(corsConfigurationProvider.corsGlobalSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/doc/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger**").permitAll()
                        .anyRequest().authenticated() // Protegge tutte le altre risorse
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()) // Configura il server come Resource Server con JWT
                )
                .authenticationManager(authenticationManager);

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    public SecurityFilterChain nosecurityFilterChain(HttpSecurity http,
                                                     CorsConfigurationProvider corsConfigurationProvider,
                                                     CustomAuthenticationManager authenticationManager) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) //
                .cors(cors -> cors.configurationSource(corsConfigurationProvider.corsGlobalSource()))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() //
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults()) //
                )
                .authenticationManager(authenticationManager);

        return http.build();
    }

}
