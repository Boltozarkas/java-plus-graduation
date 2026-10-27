package ru.practicum.feign.request;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.event.EventRequestStatusUpdateRequest;
import ru.practicum.dto.event.EventRequestStatusUpdateResult;
import ru.practicum.dto.event.EventRequestsCountDto;
import ru.practicum.dto.request.ParticipationRequestDto;

import java.util.List;

@FeignClient(
        name = "request-service",
        contextId = "requestClient",
        fallbackFactory = RequestClientFallbackFactory.class
)
public interface RequestClient {

    @GetMapping("/internal/requests/events/{eventId}/confirmed-count")
    Long getConfirmedCount(@PathVariable("eventId") Long eventId);

    @GetMapping("/internal/requests/events/counts")
    List<EventRequestsCountDto> getConfirmedCounts(@RequestParam("eventIds") List<Long> eventIds);

    @GetMapping("/internal/requests/events/{eventId}")
    List<ParticipationRequestDto> getParticipationRequestsByEventId(@PathVariable("eventId") Long eventId);
}