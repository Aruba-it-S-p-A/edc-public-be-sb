package edc.config.security.keycloak;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

@ConditionalOnProperty(prefix = "app.security", name = "enabled", havingValue = "true")
@Configuration
public class KeycloakConfiguration {

  /**
   * @param clientId
   * @return
   */
  @Bean
  Converter<Jwt, Collection<GrantedAuthority>> keycloakGrantedAuthoritiesConverter(
      @Value("${app.security.clientId}") String clientId) {
    return new KeycloakGrantedAuthoritiesConverter(clientId);
  }

  /**
   * @param converter
   * @return
   */
  @Bean
  Converter<Jwt, AbstractAuthenticationToken> keycloakJwtAuthenticationConverter(
      Converter<Jwt, Collection<GrantedAuthority>> converter) {
    return new KeycloakJwtAuthenticationConverter(converter);
  }
}
