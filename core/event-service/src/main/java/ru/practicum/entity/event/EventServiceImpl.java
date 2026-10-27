package ru.practicum.entity.event;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.practicum.EndpointHit;
import ru.practicum.StatClient;
import ru.practicum.StatsRequest;
import ru.practicum.ViewStats;
import ru.practicum.dto.category.CategoryDto;
import ru.practicum.dto.event.*;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.entity.category.Category;
import ru.practicum.entity.category.CategoryMapper;
import ru.practicum.entity.category.CategoryService;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.feign.request.RequestClient;
import ru.practicum.feign.request.RequestStatusClient;
import ru.practicum.feign.user.UserClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Optional.ofNullable;

@Service
@Slf4j
@RequiredArgsConstructor
// @Transactional(readOnly = true) с класса убран — read-only ставим точечно на read-методы
public class EventServiceImpl implements EventService {

    private final StatClient statClient;
    private final EventRepository eventRepository;
    private final CategoryService categoryService;
    private final UserClient userClient;
    private final RequestClient requestClient;
    private final RequestStatusClient requestStatusClient;
    private final TransactionTemplate transactionTemplate;

    private static final String APP_NAME = "ewm-main-service";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---------- Private ----------

    @Override
    public EventFullDto create(Long userId, NewEventDto dto) {
        CategoryDto categoryDto = categoryService.findById(dto.getCategory());
        Category category = CategoryMapper.toEntity(categoryDto);

        // Сохранение в транзакции (transactionTemplate, readOnly=false)
        Event event = transactionTemplate.execute(status -> {
            Event newEvent = EventMapper.mapToEntity(dto, category, userId);
            return eventRepository.save(newEvent);
        });

        // Feign-вызовы для DTO — ВНЕ транзакции
        UserShortDto initiator = buildInitiator(event.getInitiatorId());
        var result = EventMapper.toEventFullDto(event, initiator, 0L, 0L);
        log.info("Новое событие: {}", EventMapper.toLogEvent(result));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getById(Long userId, Long eventId) {
        Event event = getEvent(eventId);
        UserShortDto initiator = buildInitiator(event.getInitiatorId());
        Map<Long, Long> viewsMap = getViewsMap(Collections.singletonList(event));
        Long views = viewsMap.getOrDefault(event.getId(), 0L);
        Long confirmed = requestClient.getConfirmedCount(eventId);

        return EventMapper.toEventFullDto(event, initiator, confirmed, views);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> getByIds(Collection<Long> ids) {
        return eventRepository.findAllByIdInWithCategory(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getUserEvents(Long userId, Pageable pageable) {
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Long> viewsMap = getViewsMap(events);
        Map<Long, Long> confirmedMap = getConfirmedMap(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        return events.stream()
                .map(event -> EventMapper.toEventShortDto(
                        event,
                        initiatorsMap.getOrDefault(event.getInitiatorId(),
                                new UserShortDto(event.getInitiatorId(), null)),
                        confirmedMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    @Override
    public EventFullDto updateEventByCreatorId(EventParamDto eventParamDto, UpdateEventUserRequest request) {
        Long userId = eventParamDto.getUserId();

        Event updatedEvent = transactionTemplate.execute(status -> {
            Event event = getEvent(eventParamDto.getEventId());
            checkUserIsEventInitiator(userId, event);
            checkEventUpdateConditions(event, request);
            updateEventFromUserRequest(request, event);
            return eventRepository.save(event);
        });

        Long views = getViewsMap(Collections.singletonList(updatedEvent)).getOrDefault(updatedEvent.getId(), 0L);
        UserShortDto initiator = buildInitiator(updatedEvent.getInitiatorId());
        Long confirmed = requestClient.getConfirmedCount(updatedEvent.getId());

        EventFullDto eventFullDto = EventMapper.toEventFullDto(updatedEvent, initiator, confirmed, views);
        log.info("Событие обновлено создателем: {}", EventMapper.toLogEvent(eventFullDto));
        return eventFullDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getEventRequestsByCreatorId(Long userId, Long eventId) {
        Event event = getEvent(eventId);
        checkUserIsEventInitiator(userId, event);
        return requestClient.getParticipationRequestsByEventId(eventId);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateEventRequestsStatus(EventParamDto eventParamDto,
                                                                    EventRequestStatusUpdateRequest request) {
        Long userId = eventParamDto.getUserId();
        Event event = getEvent(eventParamDto.getEventId());
        checkUserIsEventInitiator(userId, event);

        return requestStatusClient.updateRequestsStatus(eventParamDto.getEventId(), request);
    }

    // ---------- Admin ----------

    @Override
    @Transactional(readOnly = true)
    public List<EventFullDto> getEventsByAdmin(EventAdminParamDto params) {
        Pageable pageable = PageRequest.of(params.getFrom() / params.getSize(), params.getSize());
        Predicate predicate = buildAdminPredicate(params);

        List<Event> events = predicate == null ?
                eventRepository.findAll(pageable).getContent() :
                eventRepository.findAll(predicate, pageable).getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Long> viewsMap = getViewsMap(events);
        Map<Long, Long> confirmedMap = getConfirmedMap(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        return events.stream()
                .map(event -> EventMapper.toEventFullDto(
                        event,
                        initiatorsMap.getOrDefault(event.getInitiatorId(),
                                new UserShortDto(event.getInitiatorId(), null)),
                        confirmedMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L)))
                .toList();
    }

    @Override
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest request) {
        Event updatedEvent = transactionTemplate.execute(status -> {
            Event event = getEvent(eventId);
            updateEventFromAdminRequest(request, event);
            applyStateAction(event, request.getStateAction());
            return eventRepository.save(event);
        });

        Long views = getViewsMap(Collections.singletonList(updatedEvent)).getOrDefault(updatedEvent.getId(), 0L);
        UserShortDto initiator = buildInitiator(updatedEvent.getInitiatorId());
        Long confirmed = requestClient.getConfirmedCount(eventId);

        var result = EventMapper.toEventFullDto(updatedEvent, initiator, confirmed, views);
        log.info("Событие обновлено админом: {}", EventMapper.toLogEvent(result));
        return result;
    }

    // ---------- Public ----------

    @Override
    public List<EventShortDto> getPublishedEvents(EventPublicParamDto params, HttpServletRequest request) {
        validateDateRange(params.getRangeStart(), params.getRangeEnd());

        LocalDateTime rangeStart = params.getRangeStart() != null ? params.getRangeStart() : LocalDateTime.now();
        Predicate predicate = buildPublicPredicate(params, rangeStart);

        sendHit(request);

        Sort sort = resolveSort(params.getSort());
        int from = params.getFrom();
        int size = params.getSize();

        List<Long> pageIds = eventRepository.findPageIds(predicate, sort, from, size);
        if (pageIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Event> events = eventRepository.findAllByIdInWithCategory(pageIds);
        Map<Long, Event> eventById = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        List<Event> ordered = pageIds.stream()
                .map(eventById::get)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Long> viewsMap = getViewsMap(ordered);
        Map<Long, Long> confirmedMap = getConfirmedMap(ordered);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(ordered);

        return ordered.stream()
                .map(e -> EventMapper.toEventShortDto(
                        e,
                        initiatorsMap.getOrDefault(e.getInitiatorId(),
                                new UserShortDto(e.getInitiatorId(), null)),
                        confirmedMap.getOrDefault(e.getId(), 0L),
                        viewsMap.getOrDefault(e.getId(), 0L)))
                .toList();
    }

    private Sort resolveSort(SortBy sortBy) {
        if (sortBy == SortBy.VIEWS) {
            return Sort.by(Sort.Order.desc("views"), Sort.Order.asc("id"));
        }
        if (sortBy == SortBy.EVENT_DATE) {
            return Sort.by(Sort.Order.asc("eventDate"));
        }
        return Sort.unsorted();
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getPublishedEventById(Long id, HttpServletRequest request) {
        Event event = findEventByIdAndState(id, EventState.PUBLISHED);

        sendHit(request);

        Long views = getViewsMap(Collections.singletonList(event)).getOrDefault(event.getId(), 0L);
        UserShortDto initiator = buildInitiator(event.getInitiatorId());
        Long confirmed = requestClient.getConfirmedCount(id);

        return EventMapper.toEventFullDto(event, initiator, confirmed, views);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> getEventsRequests(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = events.stream().map(Event::getId).toList();
        List<EventRequestsCountDto> counts = requestClient.getConfirmedCounts(ids);
        return counts.stream()
                .collect(Collectors.toMap(EventRequestsCountDto::eventId, EventRequestsCountDto::count));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> getViewsMap(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        LocalDateTime start = events.stream()
                .map(Event::getCreatedOn)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(1));

        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .toList();

        List<ViewStats> stats = statClient.getViewStats(new StatsRequest(
                start.format(formatter),
                LocalDateTime.now().plusYears(1).format(formatter),
                uris, true));

        return stats.stream()
                .collect(Collectors.toMap(
                        vs -> Long.parseLong(vs.getUri().substring("/events/".length())),
                        ViewStats::getHits));
    }

    @Override
    @Transactional(readOnly = true)
    public Event findEventByIdAndState(Long id, EventState state) {
        return eventRepository.findByIdAndState(id, state)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " was not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEventExists(Long eventId) {
        return eventRepository.existsById(eventId);
    }

    // ---------- Helpers ----------

    private Map<Long, Long> getConfirmedMap(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = events.stream().map(Event::getId).toList();
        return requestClient.getConfirmedCounts(ids).stream()
                .collect(Collectors.toMap(EventRequestsCountDto::eventId, EventRequestsCountDto::count));
    }

    private Map<Long, UserShortDto> getInitiatorsMap(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> initiatorIds = events.stream()
                .map(Event::getInitiatorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (initiatorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userClient.findByIds(initiatorIds).stream()
                .collect(Collectors.toMap(UserShortDto::getId, u -> u));
    }

    private UserShortDto buildInitiator(Long initiatorId) {
        return userClient.findById(initiatorId);
    }

    private Predicate buildAdminPredicate(EventAdminParamDto params) {
        QEvent event = QEvent.event;
        BooleanBuilder builder = new BooleanBuilder();

        if (params.getUsers() != null && !params.getUsers().isEmpty()) {
            builder.and(event.initiatorId.in(params.getUsers()));
        }
        if (params.getStates() != null && !params.getStates().isEmpty()) {
            builder.and(event.state.in(params.getStates().stream()
                    .map(stateStr -> EventState.valueOf(stateStr.toUpperCase()))
                    .toList()));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            builder.and(event.category.id.in(params.getCategories()));
        }
        if (params.getRangeStart() != null) {
            builder.and(event.eventDate.goe(params.getRangeStart()));
        }
        if (params.getRangeEnd() != null) {
            builder.and(event.eventDate.loe(params.getRangeEnd()));
        }
        return builder.getValue();
    }

    private Predicate buildPublicPredicate(EventPublicParamDto params, LocalDateTime rangeStart) {
        QEvent event = QEvent.event;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(event.state.eq(EventState.PUBLISHED));

        if (params.getText() != null && !params.getText().isBlank()) {
            String text = params.getText();
            builder.and(new BooleanBuilder()
                    .or(event.annotation.containsIgnoreCase(text))
                    .or(event.description.containsIgnoreCase(text)));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            builder.and(event.category.id.in(params.getCategories()));
        }
        if (params.getPaid() != null) {
            builder.and(event.paid.eq(params.getPaid()));
        }
        if (rangeStart != null) {
            builder.and(event.eventDate.goe(rangeStart));
        }
        if (params.getRangeEnd() != null) {
            builder.and(event.eventDate.loe(params.getRangeEnd()));
        }
        return builder.getValue();
    }

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("Range end must be after range start");
        }
    }

    private Pageable buildPageable(SortBy sort, int from, int size) {
        return SortBy.EVENT_DATE == sort ?
                PageRequest.of(from / size, size, Sort.by("eventDate").ascending())
                : PageRequest.of(from / size, size);
    }

    private void sendHit(HttpServletRequest request) {
        EndpointHit endpointHit = EndpointHit.builder()
                .app(APP_NAME)
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now().format(formatter))
                .build();
        statClient.hit(endpointHit);
        log.info("На сервер статистики отправлен endpointHit: {}", endpointHit);
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findWithRelationsById(eventId).orElseThrow(
                () -> new NotFoundException("Event with id " + eventId + " not found")
        );
    }

    private void updateEventFromUserRequest(UpdateEventUserRequest request, Event event) {
        ofNullable(request.getAnnotation()).ifPresent(event::setAnnotation);
        ofNullable(request.getDescription()).ifPresent(event::setDescription);
        ofNullable(request.getEventDate()).ifPresent(event::setEventDate);
        ofNullable(request.getLocation()).ifPresent(event::setLocation);
        ofNullable(request.getPaid()).ifPresent(event::setPaid);
        ofNullable(request.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        ofNullable(request.getRequestModeration()).ifPresent(event::setRequestModeration);
        ofNullable(request.getTitle()).ifPresent(event::setTitle);

        if (request.getCategory() != null) {
            CategoryDto categoryDto = categoryService.findById(request.getCategory());
            Category category = CategoryMapper.toEntity(categoryDto);
            event.setCategory(category);
        }

        if (request.getStateAction() == UserStateAction.CANCEL_REVIEW) {
            event.setState(EventState.CANCELED);
        }

        if (request.getStateAction() == UserStateAction.SEND_TO_REVIEW) {
            event.setState(EventState.PENDING);
        }
    }

    public void updateEventFromAdminRequest(UpdateEventAdminRequest request, Event event) {
        if (request.getEventDate() != null && request.getEventDate().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Event date cannot be in the past");
        }

        if (request.getCategory() != null) {
            CategoryDto categoryDto = categoryService.findById(request.getCategory());
            Category category = CategoryMapper.toEntity(categoryDto);
            event.setCategory(category);
        }

        ofNullable(request.getAnnotation()).ifPresent(event::setAnnotation);
        ofNullable(request.getDescription()).ifPresent(event::setDescription);
        ofNullable(request.getEventDate()).ifPresent(event::setEventDate);
        ofNullable(request.getLocation()).ifPresent(event::setLocation);
        ofNullable(request.getPaid()).ifPresent(event::setPaid);
        ofNullable(request.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        ofNullable(request.getRequestModeration()).ifPresent(event::setRequestModeration);
        ofNullable(request.getTitle()).ifPresent(event::setTitle);
    }

    private void checkUserIsEventInitiator(Long userId, Event event) {
        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException(
                    String.format("User with id %d didn't initiate event with id %d", userId, event.getId())
            );
        }
    }

    private void checkEventUpdateConditions(Event event, UpdateEventUserRequest request) {
        if (event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }
        if (request.getEventDate() != null && !request.getEventDate().isAfter(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least two hours in the future.");
        }
    }

    private void applyStateAction(Event event, AdminStateAction stateAction) {
        if (stateAction == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        switch (stateAction) {
            case AdminStateAction.PUBLISH_EVENT -> {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                }
                if (event.getEventDate().isBefore(now.plusHours(1))) {
                    throw new ConflictException("Cannot publish the event because event date is less than 1 hour from now.");
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(now);
            }
            case AdminStateAction.REJECT_EVENT -> {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject the event because it's already published.");
                }
                event.setState(EventState.CANCELED);
            }
            default -> throw new ConflictException("Invalid state action: " + stateAction);
        }
    }
}