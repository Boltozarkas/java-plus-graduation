package ru.practicum.entity.request;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventRequestStatusUpdateRequest;
import ru.practicum.dto.event.EventRequestStatusUpdateResult;
import ru.practicum.dto.event.EventRequestsCountDto;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.ewm.client.CollectorClient;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.event.EventClient;
import ru.practicum.feign.user.UserClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
// УБРАЛИ @Transactional(readOnly = true) с класса
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final TransactionTemplate transactionTemplate;
    private final CollectorClient collectorClient;

    @Override
    public ParticipationRequestDto saveRequest(Long userId, Long eventId) {
        // 1. Все проверки и сетевые вызовы — ВНЕ транзакции
        if (existsByUserIdAndEventId(userId, eventId)) {
            throw new ConflictException("Attempt to send a duplicate request.");
        }

        UserShortDto user = userClient.findById(userId);
        EventFullDto event = eventClient.findById(eventId);

        checkUserRequestForEvent(event, user);

        boolean autoConfirmed = !Boolean.TRUE.equals(event.getRequestModeration())
                || event.getParticipantLimit() == 0;

        // 2. Только сохранение в БД — внутри транзакции
        Request savedRequest = transactionTemplate.execute(status -> {
            Request request = RequestMapper.toRequest(eventId, userId, autoConfirmed);
            return requestRepository.save(request);
        });

        // Отправляем информацию о регистрации в Collector
        try {
            collectorClient.sendRegister(userId, eventId);
        } catch (Exception e) {
            log.error("Failed to send register action to collector", e);
        }

        var requestDto = RequestMapper.toRequestDto(savedRequest);
        log.info("Новая заявка на событие: {}", requestDto);
        return requestDto;
    }

    @Override
    @Transactional(readOnly = true)
    public Boolean isUserConfirmed(Long userId, Long eventId) {
        return requestRepository.existsByRequesterIdAndEventIdAndStatus(userId, eventId, RequestStatus.CONFIRMED);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        Request request = getRequest(requestId);

        if (!request.getRequesterId().equals(userId)) {
            throw new ConflictException("You can only cancel your own participation requests.");
        }

        request.setStatus(RequestStatus.CANCELED);

        var requestDto = RequestMapper.toRequestDto(request);
        log.info("Заявка на событие отменена: {}", requestDto);

        return requestDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        userClient.findById(userId);
        return RequestMapper.toRequestDto(requestRepository.findByRequesterId(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventRequestsCountDto> countByEventIdsAndStatus(Set<Long> ids, RequestStatus status) {
        return requestRepository.countByEventIdInAndStatus(ids, status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByEventIdAndStatus(Long eventId, RequestStatus status) {
        return requestRepository.countByEventIdAndStatus(eventId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getParticipationRequestsByEventId(Long eventId) {
        return RequestMapper.toRequestDto(requestRepository.findByEventId(eventId));
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatus(Long eventId,
                                                               EventRequestStatusUpdateRequest request) {
        // 1. Загружаем данные
        Set<Long> requestIds = request.getRequestIds();
        RequestStatus targetStatus = request.getStatus();
        EventFullDto event = eventClient.findById(eventId);
        List<Request> requests = requestRepository.findByIdInAndEventId(requestIds, eventId);

        // 2. Проверяем, можно ли подтверждать заявки
        long confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        long participantLimit = event.getParticipantLimit() == null ? 0L : event.getParticipantLimit();
        long remainedRequests = validateConfirmationRules(targetStatus, event, participantLimit, confirmedRequests);

        // 3. Распределяем заявки по статусам
        List<Request> confirmed = new ArrayList<>();
        List<Request> rejected = new ArrayList<>();
        processRequests(requests, targetStatus, remainedRequests, confirmed, rejected);

        // 4. Формируем результат
        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult(
                RequestMapper.toRequestDto(confirmed),
                RequestMapper.toRequestDto(rejected)
        );
        log.info("Обновлены статусы заявок на событие {}: {}", eventId, result);

        return result;
    }

    /**
     * Проверяет, можно ли подтвердить заявки для данного события.
     *
     * @return оставшийся лимит заявок (remainedRequests).
     */
    private long validateConfirmationRules(RequestStatus targetStatus,
                                           EventFullDto event,
                                           long participantLimit,
                                           long confirmedRequests) {
        long remainedRequests = participantLimit - confirmedRequests;

        if (targetStatus == RequestStatus.CONFIRMED) {
            if (participantLimit == 0L || !Boolean.TRUE.equals(event.getRequestModeration())) {
                throw new ConflictException("Confirmation is not required or allowed for this event configuration.");
            }
            if (remainedRequests <= 0L) {
                throw new ConflictException("The participant limit has been reached");
            }
        }
        return remainedRequests;
    }

    /**
     * Проходит по списку заявок и изменяет их статусы в соответствии с правилами.
     */
    private void processRequests(List<Request> requests,
                                 RequestStatus targetStatus,
                                 long remainedRequests,
                                 List<Request> confirmed,
                                 List<Request> rejected) {
        for (Request req : requests) {
            // Валидация статуса заявки
            if (req.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING. Request with id="
                        + req.getId() + " has status " + req.getStatus());
            }

            // Если целимся в REJECTED — просто отклоняем
            if (targetStatus == RequestStatus.REJECTED) {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(req);
                continue;
            }

            // Если лимит кончился — отклоняем
            if (remainedRequests <= 0L) {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(req);
                continue;
            }

            // Иначе подтверждаем
            req.setStatus(RequestStatus.CONFIRMED);
            confirmed.add(req);
            remainedRequests--;
        }
    }

    private boolean existsByUserIdAndEventId(Long userId, Long eventId) {
        return requestRepository.existsByRequesterIdAndEventId(userId, eventId);
    }

    private Request getRequest(Long requestId) {
        return requestRepository.findById(requestId).orElseThrow(
                () -> new NotFoundException("Request with id " + requestId + " not found."));
    }

    private void checkUserRequestForEvent(EventFullDto event, UserShortDto user) {
        if (event.getState() == null || !"PUBLISHED".equals(event.getState())) {
            throw new ConflictException("The Participation in unpublished events is forbidden.");
        }
        if (event.getInitiator() != null && event.getInitiator().getId().equals(user.getId())) {
            throw new ConflictException("The initiator can't request to join their own event.");
        }
        if (event.getParticipantLimit() != null && event.getParticipantLimit() != 0
                && event.getConfirmedRequests() != null
                && event.getConfirmedRequests() >= event.getParticipantLimit()) {
            throw new ConflictException("The participant limit has been reached.");
        }
    }
}