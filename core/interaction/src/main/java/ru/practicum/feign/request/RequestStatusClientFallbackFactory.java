package ru.practicum.feign.request;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.practicum.dto.event.EventRequestStatusUpdateRequest;
import ru.practicum.dto.event.EventRequestStatusUpdateResult;
import ru.practicum.exception.ServiceUnavailableException;

@Component
public class RequestStatusClientFallbackFactory implements FallbackFactory<RequestStatusClient> {

    @Override
    public RequestStatusClient create(Throwable cause) {
        return new RequestStatusClient() {
            @Override
            public EventRequestStatusUpdateResult updateRequestsStatus(Long eventId,
                                                                       EventRequestStatusUpdateRequest request) {
                // Если причина — FeignException.Conflict (409), пробрасываем дальше,
                // чтобы EventServiceErrorHandler.handleFeignConflict вернул 409.
                // Если причина — сетевая ошибка/5xx — возвращаем ServiceUnavailableException (503).
                if (cause instanceof feign.FeignException.Conflict conflict) {
                    throw conflict;
                }
                throw new ServiceUnavailableException("Request service is unavailable", cause);
            }
        };
    }
}