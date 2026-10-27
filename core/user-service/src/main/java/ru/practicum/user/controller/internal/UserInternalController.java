package ru.practicum.user.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.user.UserFullDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.entity.user.User;
import ru.practicum.entity.user.UserMapper;
import ru.practicum.entity.user.UserRepository;
import ru.practicum.entity.user.UserService;

import java.util.List;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserRepository userRepository;
    private final UserService userService;

    @GetMapping("/{id}")
    public UserShortDto findById(@PathVariable Long id) {
        User user = userService.findById(id);
        return UserMapper.toShortDto(user);
    }

    @GetMapping("/{id}/exists")
    public boolean existsById(@PathVariable Long id) {
        return userRepository.existsById(id);
    }

    @GetMapping("/{id}/full")
    public UserFullDto findFullById(@PathVariable Long id) {
        User user = userService.findById(id);
        return UserMapper.toUserFullDto(user);
    }

    @PostMapping("/{id}/warn")
    public UserFullDto giveWarning(@PathVariable Long id) {
        return userService.addAdminWarning(id);
    }

    @PostMapping("/{id}/comments-count/increment")
    public UserFullDto incrementCommentsCount(@PathVariable Long id) {
        return userService.incrementCommentsCount(id);
    }

    @PostMapping("/{id}/comments-count/decrement")
    public UserFullDto decrementCommentsCount(@PathVariable Long id) {
        return userService.decrementCommentsCount(id);
    }

    @GetMapping("/batch")
    public List<UserShortDto> findByIds(@RequestParam("ids") List<Long> ids) {
        return userService.findByIds(ids).stream()
                .map(UserMapper::toShortDto)
                .toList();
    }
}