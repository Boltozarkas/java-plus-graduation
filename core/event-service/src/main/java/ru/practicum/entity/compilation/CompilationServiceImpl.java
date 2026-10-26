package ru.practicum.entity.compilation;

import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.practicum.dto.compilation.CompilationDto;
import ru.practicum.dto.compilation.CompilationsParamDto;
import ru.practicum.dto.compilation.NewCompilationDto;
import ru.practicum.dto.compilation.UpdateCompilationRequest;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.entity.event.Event;
import ru.practicum.entity.event.EventService;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.user.UserClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
// @Transactional(readOnly = true) с класса убран — read-only ставим точечно на read-методы
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventService eventService;
    private final UserClient userClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<CompilationDto> getAll(CompilationsParamDto dto) {
        int pageNumber = dto.getFrom() / dto.getSize();
        PageRequest pageRequest = PageRequest.of(pageNumber, dto.getSize());
        List<Compilation> compilations;
        if (dto.getPinned() == null) {
            compilations = compilationRepository.findAll(pageRequest).getContent();
        } else {
            BooleanExpression condition = QCompilation.compilation.pinned.eq(dto.getPinned());
            compilations = compilationRepository.findAll(condition, pageRequest).getContent();
        }

        if (compilations.isEmpty()) {
            return List.of();
        }

        List<Event> events = compilations.stream()
                .map(Compilation::getEvents)
                .flatMap(Collection::stream)
                .toList();

        Map<Long, Long> hitsMap = eventService.getViewsMap(events);
        Map<Long, Long> eventsIdRequestsCount = eventService.getEventsRequests(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        return compilations.stream()
                .map(compilation -> CompilationMapper.toDto(compilation, hitsMap, eventsIdRequestsCount, initiatorsMap))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompilationDto getById(Long compId) {
        Compilation compilation = getByIdWithEvents(compId);
        List<Event> events = compilation.getEvents().stream().toList();
        Map<Long, Long> hitsMap = eventService.getViewsMap(events);
        Map<Long, Long> eventsIdRequestsCount = eventService.getEventsRequests(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        return CompilationMapper.toDto(compilation, hitsMap, eventsIdRequestsCount, initiatorsMap);
    }

    @Override
    public CompilationDto create(NewCompilationDto dto) {
        if (compilationRepository.existsByTitle(dto.getTitle())) {
            throw new ConflictException("Compilation with title " + dto.getTitle() + " already exists");
        }

        // events теперь с инициализированной category (@EntityGraph в EventRepository)
        List<Event> events = eventService.getByIds(dto.getEvents());

        Compilation entity = transactionTemplate.execute(status -> {
            Compilation compilation = CompilationMapper.toEntity(dto, new HashSet<>(events));
            return compilationRepository.save(compilation);
        });

        // Feign-вызовы — ВНЕ транзакции
        Map<Long, Long> hitsMap = eventService.getViewsMap(events);
        Map<Long, Long> eventsIdRequestsCount = eventService.getEventsRequests(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        var compilationDto = CompilationMapper.toDto(entity, hitsMap, eventsIdRequestsCount, initiatorsMap);
        log.info("Новая подборка событий: {}", CompilationMapper.toLog(compilationDto));

        return compilationDto;
    }

    @Override
    @Transactional
    public void delete(Long compId) {
        Compilation compilation = compilationRepository.findById(compId).orElseThrow(
                () -> new NotFoundException(String.format("Compilation with id=%d not found", compId))
        );
        compilationRepository.delete(compilation);
        log.info("Удалена подборка событий id: {}", compilation.getId());
    }

    @Override
    public CompilationDto update(Long compId, UpdateCompilationRequest request) {
        // Всё (загрузка + updateFields + save) — в одной транзакции.
        // findWithEventsById с @EntityGraph({"events", "events.category"}) → events и category инициализированы.
        Compilation updatedEntity = transactionTemplate.execute(status -> {
            Compilation compilation = getByIdWithEvents(compId);
            updateFields(compilation, request);
            return compilationRepository.save(compilation);
        });

        List<Event> events = updatedEntity.getEvents().stream().toList();

        // Feign-вызовы — ВНЕ транзакции
        Map<Long, Long> hitsMap = eventService.getViewsMap(events);
        Map<Long, Long> eventsIdRequestsCount = eventService.getEventsRequests(events);
        Map<Long, UserShortDto> initiatorsMap = getInitiatorsMap(events);

        var compilationDto = CompilationMapper.toDto(updatedEntity, hitsMap, eventsIdRequestsCount, initiatorsMap);
        log.info("Подборка событий обновлена: {}", CompilationMapper.toLog(compilationDto));
        return compilationDto;
    }

    private Compilation getByIdWithEvents(Long compId) {
        return compilationRepository.findWithEventsById(compId).orElseThrow(
                () -> new NotFoundException(String.format("Compilation with id=%d not found", compId))
        );
    }

    private void updateFields(Compilation old, UpdateCompilationRequest request) {

        if (request.getTitle() != null) {
            if (!old.getTitle().equals(request.getTitle())) {
                if (compilationRepository.existsByTitle(request.getTitle())) {
                    throw new ConflictException(String.format("Compilation '%s' already exists", request.getTitle()));
                }

                old.setTitle(request.getTitle());
            }
        }

        if (request.getPinned() != null && !old.getPinned().equals(request.getPinned())) {
            old.setPinned(request.getPinned());
        }

        Set<Long> oldIds = old.getEvents().stream()
                .map(Event::getId)
                .collect(Collectors.toUnmodifiableSet());

        if (request.getEvents() != null && !oldIds.equals(request.getEvents())) {
            // getByIds возвращает events с инициализированной category
            List<Event> newEvents = eventService.getByIds(request.getEvents());
            old.setEvents(new HashSet<>(newEvents));
        }
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
}