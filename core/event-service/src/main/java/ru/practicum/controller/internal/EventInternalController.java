package ru.practicum.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.entity.event.Event;
import ru.practicum.entity.event.EventMapper;
import ru.practicum.entity.event.EventRepository;
import ru.practicum.entity.event.EventState;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.request.RequestClient;
import ru.practicum.feign.user.UserClient;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventInternalController {

    private final EventRepository eventRepository;
    private final UserClient userClient;
    private final RequestClient requestClient;

    @GetMapping("/{id}")
    public EventFullDto findById(@PathVariable Long id) {
        Event event = eventRepository.findWithRelationsById(id).orElseThrow(
                () -> new NotFoundException("Event with id " + id + " not found"));
        UserShortDto initiator = userClient.findById(event.getInitiatorId());
        Long confirmed = requestClient.getConfirmedCount(id);
        return EventMapper.toEventFullDto(event, initiator, confirmed, 0L);
    }

    @GetMapping("/{id}/published")
    public EventShortDto findPublishedById(@PathVariable Long id) {
        Event event = eventRepository.findByIdAndState(id, EventState.PUBLISHED).orElseThrow(
                () -> new NotFoundException("Event with id " + id + " not published"));
        UserShortDto initiator = userClient.findById(event.getInitiatorId());
        Long confirmed = requestClient.getConfirmedCount(id);
        return EventMapper.toEventShortDto(event, initiator, confirmed, 0L);
    }

    @GetMapping("/{id}/exists")
    public boolean existsById(@PathVariable Long id) {
        return eventRepository.existsById(id);
    }
}