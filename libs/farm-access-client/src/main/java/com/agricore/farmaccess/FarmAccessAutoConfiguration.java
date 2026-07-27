package com.agricore.farmaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agricore.security.AgricoreSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({FarmAccessProperties.class, AgricoreSecurityProperties.class})
public class FarmAccessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FarmAccessClient.class)
    FarmAccessClient farmAccessClient(
            FarmAccessProperties farmAccessProperties,
            AgricoreSecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(farmAccessProperties.validatedConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(farmAccessProperties.validatedReadTimeout());
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        return new DefaultFarmAccessClient(
                builder,
                farmAccessProperties,
                securityProperties.isDevMode(),
                objectMapper
        );
    }
}
