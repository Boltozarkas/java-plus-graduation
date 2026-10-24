package ru.practicum.feign.user;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.practicum.dto.user.UserFullDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.exception.ServiceUnavailableException;

import java.util.List;

@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {

            /**
             * Мягкий fallback: при недоступности user-service
             * возвращаем заглушку (id, null), чтобы GET /events продолжал работать.
             * Надёжность важнее корректности имени инициатора.
             */
            @Override
            public UserShortDto findById(Long id) {
                return new UserShortDto(id, null);
            }

            /**
             * Метод используется только в user-service (для проверки существования),
             * но на случай, если кто-то ещё вызовет — вернём false.
             */
            @Override
            public boolean existsById(Long id) {
                return false;
            }

            // Все методы ниже — критичные: без реального юзера операция невозможна
            @Override
            public UserFullDto findFullById(Long id) {
                throw new ServiceUnavailableException("User service is unavailable", cause);
            }

            @Override
            public UserFullDto giveWarning(Long id) {
                throw new ServiceUnavailableException("User service is unavailable", cause);
            }

            @Override
            public UserFullDto incrementCommentsCount(Long id) {
                throw new ServiceUnavailableException("User service is unavailable", cause);
            }

            @Override
            public UserFullDto decrementCommentsCount(Long id) {
                throw new ServiceUnavailableException("User service is unavailable", cause);
            }

            @Override
            public List<UserShortDto> findByIds(List<Long> ids) {
                if (ids == null || ids.isEmpty()) {
                    return List.of();
                }
                return ids.stream()
                        .map(id -> new UserShortDto(id, null))
                        .toList();
            }
        };
    }
}