package crudlivros_api.sistemasdestribuidos.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfiguration {

    @Bean
    HttpClient jdkHttpClient(OrchestratorProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.proxyConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
