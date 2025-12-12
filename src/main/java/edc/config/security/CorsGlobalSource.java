package edc.config.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Component
public class CorsGlobalSource implements CorsConfigurationSource {

  @Value("${app.security.cors.enableGlobalCors:false}")
  private boolean enableGlobalCors;

  @Value("${app.security.cors.origins:none}")
  private String[] origins;

  @Value("${app.security.cors.exposeHeaders:Access-Control-Allow-Origin}")
  private String[] exposeHeaders;

  @Value("${app.security.cors.methods:GET,POST,DELETE,PUT,OPTIONS}")
  private String[] methods;

  @Value("${app.security.cors.max-age:3600}")
  private long maxAge;


    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;

    @PostConstruct
    public void init() {
        allowedOrigins = Arrays.asList(origins);
        allowedMethods = Arrays.asList(methods);
        allowedHeaders = Arrays.asList(exposeHeaders);
        exposedHeaders = Arrays.asList(exposeHeaders);
    }

  @Override
  public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
    CorsConfiguration config = new CorsConfiguration();
    if(enableGlobalCors) {
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);
        config.setAllowCredentials(false);
    } else {
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(allowedMethods);
        config.setAllowCredentials(true);
        config.setAllowedHeaders(allowedHeaders);
        config.setExposedHeaders(exposedHeaders);
        config.setMaxAge(maxAge);
    }
    return config;
  }
}
