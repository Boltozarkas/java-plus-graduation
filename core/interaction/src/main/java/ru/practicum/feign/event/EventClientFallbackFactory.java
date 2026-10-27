package ru.practicum.feign.event;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.exception.ServiceUnavailableException;

@Component
public class EventClientFallbackFactory implements FallbackFactory<EventClient> {

    @Override
    public EventClient create(Throwable cause) {
        return new EventClient() {
            @Override
            public EventFullDto findById(Long id) {
                throw new ServiceUnavailableException("Event service is unavailable", cause);
            }

            @Override
            public boolean existsById(Long id) {
                throw new ServiceUnavailableException("Event service is unavailable", cause);
            }
        };
    }
}