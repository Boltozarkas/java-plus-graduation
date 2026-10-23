package ru.practicum.feign.request;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.practicum.dto.event.EventRequestStatusUpdateRequest;
import ru.practicum.dto.event.EventRequestStatusUpdateResult;

@FeignClient(
        name = "request-service",
        contextId = "requestStatusClient",
        fallbackFactory = RequestStatusClientFallbackFactory.class
)
public interface RequestStatusClient {
    @PostMapping("/internal/requests/events/{eventId}/status")
    EventRequestStatusUpdateResult updateRequestsStatus(
            @PathVariable("eventId") Long eventId,
            @RequestBody EventRequestStatusUpdateRequest request);
}
