package ru.practicum.entity.request;

import lombok.experimental.UtilityClass;
import ru.practicum.dto.request.ParticipationRequestDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@UtilityClass
public class RequestMapper {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static Request toRequest(Long eventId, Long userId, boolean autoConfirmed) {
        return Request.builder()
                .eventId(eventId)
                .requesterId(userId)
                .createdAt(LocalDateTime.now())
                .status(autoConfirmed ? RequestStatus.CONFIRMED : RequestStatus.PENDING)
                .build();
    }

    public static ParticipationRequestDto toRequestDto(Request request) {
        return ParticipationRequestDto.builder()
                .id(request.getId())
                .created(request.getCreatedAt().format(formatter))
                .eventId(request.getEventId())
                .requesterId(request.getRequesterId())
                .status(request.getStatus().name())
                .build();
    }

    public static List<ParticipationRequestDto> toRequestDto(List<Request> requests) {
        return requests.stream()
                .map(RequestMapper::toRequestDto)
                .toList();
    }
}