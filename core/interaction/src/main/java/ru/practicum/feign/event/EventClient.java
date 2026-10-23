package ru.practicum.feign.event;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.dto.event.EventFullDto;

@FeignClient(name = "event-service", fallbackFactory = EventClientFallbackFactory.class)
public interface EventClient {

    @GetMapping("/internal/events/{id}")
    EventFullDto findById(@PathVariable("id") Long id);

    @GetMapping("/internal/events/{id}/exists")
    boolean existsById(@PathVariable("id") Long id);
}