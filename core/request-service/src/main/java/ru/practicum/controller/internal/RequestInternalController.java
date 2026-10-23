package ru.practicum.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.event.EventRequestStatusUpdateRequest;
import ru.practicum.dto.event.EventRequestStatusUpdateResult;
import ru.practicum.dto.event.EventRequestsCountDto;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.entity.request.RequestService;
import ru.practicum.entity.request.RequestStatus;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class RequestInternalController {

    private final RequestService requestService;

    @GetMapping("/events/{eventId}/confirmed-count")
    public Long getConfirmedCount(@PathVariable Long eventId) {
        return requestService.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    @GetMapping("/events/counts")
    public List<EventRequestsCountDto> getConfirmedCounts(@RequestParam("eventIds") Set<Long> eventIds) {
        return requestService.countByEventIdsAndStatus(eventIds, RequestStatus.CONFIRMED);
    }

    @GetMapping("/events/{eventId}")
    public List<ParticipationRequestDto> getParticipationRequestsByEventId(@PathVariable Long eventId) {
        return requestService.getParticipationRequestsByEventId(eventId);
    }

    @PostMapping("/events/{eventId}/status")
    public EventRequestStatusUpdateResult updateRequestsStatus(
            @PathVariable Long eventId,
            @RequestBody EventRequestStatusUpdateRequest request) {
        return requestService.updateRequestsStatus(eventId, request.getRequestIds(), request.getStatus());
    }
}