package ru.practicum.feign.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.user.UserFullDto;
import ru.practicum.dto.user.UserShortDto;

import java.util.List;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    @GetMapping("/internal/users/{id}")
    UserShortDto findById(@PathVariable("id") Long id);

    @GetMapping("/internal/users/{id}/exists")
    boolean existsById(@PathVariable("id") Long id);

    @GetMapping("/internal/users/{id}/full")
    UserFullDto findFullById(@PathVariable("id") Long id);

    @PostMapping("/internal/users/{id}/warn")
    UserFullDto giveWarning(@PathVariable("id") Long id);

    @PostMapping("/internal/users/{id}/comments-count/increment")
    UserFullDto incrementCommentsCount(@PathVariable("id") Long id);

    @PostMapping("/internal/users/{id}/comments-count/decrement")
    UserFullDto decrementCommentsCount(@PathVariable("id") Long id);

    @GetMapping("/internal/users/batch")
    List<UserShortDto> findByIds(@RequestParam("ids") List<Long> ids); // <-- добавили
}