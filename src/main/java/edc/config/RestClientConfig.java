package edc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RestClientConfig {

    private final ExternalApiProperties externalApiProperties;
    private final KeycloakProperties keycloakProperties;

    HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Bean(name = "externalApiRestClient")
    public RestClient externalApiRestClient(RestClient.Builder restClientBuilder) {
        log.trace("Configuring RestClient externalApiRestClient with base URL: {}", externalApiProperties.getBaseUrl());
        return restClientBuilder
                .baseUrl(externalApiProperties.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean(name = "keycloakRestClient")
    public RestClient kkRestClient(RestClient.Builder restClientBuilder) {
        log.trace("Configuring RestClient keycloakRestClient with base URL: {}", keycloakProperties.getBaseUrl());
        return restClientBuilder
                .baseUrl(keycloakProperties.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean()
    @Primary
    public RestClient restClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }

    // Optional: custom timeout configuration
    /*
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }
    */
}
