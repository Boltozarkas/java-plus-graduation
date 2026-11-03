package ru.practicum.feign.request;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.practicum.dto.event.EventRequestsCountDto;
import ru.practicum.dto.request.ParticipationRequestDto;

import java.util.Collections;
import java.util.List;

@Component
public class RequestClientFallbackFactory implements FallbackFactory<RequestClient> {

    @Override
    public RequestClient create(Throwable cause) {
        return new RequestClient() {
            @Override
            public Long getConfirmedCount(Long eventId) {
                return 0L;
            }

            @Override
            public List<EventRequestsCountDto> getConfirmedCounts(List<Long> eventIds) {
                return Collections.emptyList();
            }

            @Override
            public List<ParticipationRequestDto> getParticipationRequestsByEventId(Long eventId) {
                return Collections.emptyList();
            }

            @Override
            public Boolean isUserConfirmed(Long userId, Long eventId) {
                return false;
            }
        };
    }
}