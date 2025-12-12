package edc.config.security.keycloak;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Objects;

@Slf4j
public final class KeycloakJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  /** */
  private static final String USERNAME_CLAIM = "preferred_username";

  /** */
  private final Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter;

  /**
   * @param jwtGrantedAuthoritiesConverter
   */
  public KeycloakJwtAuthenticationConverter(
      Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter) {
    this.jwtGrantedAuthoritiesConverter = jwtGrantedAuthoritiesConverter;
  }

  /**
   * @param source
   * @return
   */
  @Override
  public AbstractAuthenticationToken convert(Jwt source) {
    Collection<GrantedAuthority> grantedAuthorities =
        Objects.requireNonNull(this.jwtGrantedAuthoritiesConverter.convert(source));
    return new JwtAuthenticationToken(source, grantedAuthorities, extractUsername(source));
  }

  /**
   * @param jwt
   * @return
   */
  private static String extractUsername(Jwt jwt) {
    return jwt.hasClaim(USERNAME_CLAIM) ? jwt.getClaimAsString(USERNAME_CLAIM) : jwt.getSubject();
  }
}
