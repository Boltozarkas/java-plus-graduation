package ru.practicum;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class StatClient {

    private final DiscoveryClient discoveryClient;
    private final RestClient restClient;
    private final String statsServiceId;
    private final RetryTemplate retryTemplate;

    public StatClient(DiscoveryClient discoveryClient,
                      RestClient.Builder builder,
                      @Value("${stats.service.id:stats-server}") String statsServiceId) {
        this.discoveryClient = discoveryClient;
        this.restClient = builder.build();
        this.statsServiceId = statsServiceId;

        RetryTemplate template = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(3000L);
        template.setBackOffPolicy(fixedBackOffPolicy);

        MaxAttemptsRetryPolicy retryPolicy = new MaxAttemptsRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        template.setRetryPolicy(retryPolicy);

        this.retryTemplate = template;
    }

    public void hit(EndpointHit endpointHit) {
        try {
            restClient.post()
                    .uri(makeUri("/hit"))
                    .body(endpointHit)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Ловим всё: RestClientException, RuntimeException из makeUri,
            // NoSuchElementException при пустом getInstances — hit не должен ломать бизнес-логику.
            log.error("Stats service unavailable: {}", e.getMessage(), e);
        }
    }

    public List<ViewStats> getViewStats(StatsRequest statsRequest) {
        try {
            URI uri = UriComponentsBuilder.fromUri(makeUri("/stats"))
                    .queryParam("start", statsRequest.getStartTime())
                    .queryParam("end", statsRequest.getEndTime())
                    .queryParam("uris", statsRequest.getUris())
                    .queryParam("unique", statsRequest.getUnique())
                    .build()
                    .toUri();

            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ViewStats>>() {
                    });
        } catch (Exception e) {
            log.error("Stats service unavailable: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private URI makeUri(String path) {
        ServiceInstance instance = retryTemplate.execute(cxt -> getInstance());
        return URI.create("http://" + instance.getHost() + ":" + instance.getPort() + path);
    }

    private ServiceInstance getInstance() {
        List<ServiceInstance> instances = discoveryClient.getInstances(statsServiceId);
        if (instances == null || instances.isEmpty()) {
            throw new RuntimeException(
                    "Сервис статистики с id: " + statsServiceId + " не зарегистрирован в Eureka");
        }
        return instances.getFirst();
    }
}