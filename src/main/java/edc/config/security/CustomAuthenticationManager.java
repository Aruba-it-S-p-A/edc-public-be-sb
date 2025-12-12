package edc.config.security;

import lombok.SneakyThrows;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(prefix = "app.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
public class CustomAuthenticationManager implements AuthenticationManager {

  /** */
  public static final String KEY_CLAIM_EMAIL = "email";

  /** */
  private final JwtDecoder jwtDecoder;

  /** */
  @Autowired private BeanFactory beanFactory;

  /**
   * @param jwtDecoder
   */
  public CustomAuthenticationManager(JwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;
  }

  /**
   * @param authentication
   * @return
   * @throws AuthenticationException
   */
  @SneakyThrows
  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    JwtAuthenticationProvider provider = new JwtAuthenticationProvider(this.jwtDecoder);
    provider.setJwtAuthenticationConverter(
        (Converter<Jwt, ? extends AbstractAuthenticationToken>)
            beanFactory.getBean("keycloakJwtAuthenticationConverter"));
    Authentication authenticate = provider.authenticate(authentication);
    if (authenticate.getPrincipal() instanceof Jwt
        && ((Jwt) authenticate.getPrincipal()).hasClaim(KEY_CLAIM_EMAIL)) {
      authenticate.setAuthenticated(true);
    }
    return authenticate;
  }
}
