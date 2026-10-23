package ru.practicum.entity.request;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventRequestStatusUpdateResult;
import ru.practicum.dto.event.EventRequestsCountDto;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.event.EventClient;
import ru.practicum.feign.user.UserClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final UserClient userClient;
    private final EventClient eventClient;

    @Override
    @Transactional
    public ParticipationRequestDto saveRequest(Long userId, Long eventId) {
        if (existsByUserIdAndEventId(userId, eventId)) {
            throw new ConflictException("Attempt to send a duplicate request.");
        }

        UserShortDto user = userClient.findById(userId);
        EventFullDto event = eventClient.findById(eventId);

        checkUserRequestForEvent(event, user);

        boolean autoConfirmed = !Boolean.TRUE.equals(event.getRequestModeration())
                || event.getParticipantLimit() == 0;
        Request request = RequestMapper.toRequest(eventId, userId, autoConfirmed);
        request = requestRepository.save(request);

        var requestDto = RequestMapper.toRequestDto(request);
        log.info("Новая заявка на событие: {}", requestDto);

        return requestDto;
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
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        userClient.findById(userId);

        return RequestMapper.toRequestDto(requestRepository.findByRequesterId(userId));
    }

    @Override
    public List<EventRequestsCountDto> countByEventIdsAndStatus(Set<Long> ids, RequestStatus status) {
        return requestRepository.countByEventIdInAndStatus(ids, status);
    }

    @Override
    public long countByEventIdAndStatus(Long eventId, RequestStatus status) {
        return requestRepository.countByEventIdAndStatus(eventId, status);
    }

    @Override
    public List<ParticipationRequestDto> getParticipationRequestsByEventId(Long eventId) {
        return RequestMapper.toRequestDto(requestRepository.findByEventId(eventId));
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatus(Long eventId,
                                                               Set<Long> requestIds,
                                                               RequestStatus targetStatus) {
        EventFullDto event = eventClient.findById(eventId);

        List<Request> requests = requestRepository.findByIdInAndEventId(requestIds, eventId);

        long confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        long participantLimit = event.getParticipantLimit() == null ? 0L : event.getParticipantLimit();
        long remainedRequests = participantLimit - confirmedRequests;

        if (targetStatus == RequestStatus.CONFIRMED) {
            if (participantLimit == 0L || !Boolean.TRUE.equals(event.getRequestModeration())) {
                throw new ConflictException("Confirmation is not required or allowed for this event configuration.");
            }
            if (remainedRequests <= 0L) {
                throw new ConflictException("The participant limit has been reached");
            }
        }

        List<Request> confirmed = new ArrayList<>();
        List<Request> rejected = new ArrayList<>();

        for (Request req : requests) {
            if (req.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING. Request with id="
                        + req.getId() + " has status " + req.getStatus());
            }

            if (targetStatus == RequestStatus.REJECTED) {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(req);
                continue;
            }

            if (remainedRequests <= 0L) {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(req);
                continue;
            }

            req.setStatus(RequestStatus.CONFIRMED);
            confirmed.add(req);
            remainedRequests--;
        }

        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult(
                RequestMapper.toRequestDto(confirmed),
                RequestMapper.toRequestDto(rejected)
        );
        log.info("Обновлены статусы заявок на событие {}: {}", eventId, result);

        return result;
    }

    private boolean existsByUserIdAndEventId(Long userId, Long eventId) {
        return requestRepository.existsByRequesterIdAndEventId(userId, eventId);
    }

    private Request getRequest(Long requestId) {
        return requestRepository.findById(requestId).orElseThrow(
                () -> new NotFoundException("Request with id " + requestId + " not found.")
        );
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