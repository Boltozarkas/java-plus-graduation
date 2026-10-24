package ru.practicum.entity.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.comment.UserCommentAdminDto;
import ru.practicum.dto.user.NewUserRequest;
import ru.practicum.dto.user.UserDto;
import ru.practicum.dto.user.UserFullDto;
import ru.practicum.dto.user.UserParamDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor()
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDto saveUser(NewUserRequest userRequest) {
        if (userRepository.existsByMail(userRequest.getEmail())) {
            throw new ConflictException("User with this email already exists");
        }

        User savedUser = userRepository.save(UserMapper.toUser(userRequest));
        log.info("Новый пользователь {}", savedUser);

        return UserMapper.toUserDto(savedUser);
    }

    @Override
    public List<UserDto> getUsers(UserParamDto userParamDto) {

        if (userParamDto.getIds() != null && !userParamDto.getIds().isEmpty()) {
            List<Long> ids = userParamDto.getIds().stream().toList();
            return UserMapper.toUserDtos(userRepository.findAllByIdIn(ids));
        }

        int page = userParamDto.getFrom() / userParamDto.getSize();
        int pageSize = userParamDto.getSize();
        List<User> users = userRepository.findAll(PageRequest.of(page, pageSize)).getContent();

        return UserMapper.toUserDtos(users);
    }

    @Override
    @Transactional
    public void deleteUserById(Long userId) {
        checkUserExist(userId);
        userRepository.deleteById(userId);
    }

    @Override
    public User findById(Long userId) {
        return userRepository.findById(userId).orElseThrow(
                () -> new NotFoundException("User with id: " + userId + " does not exist"));
    }

    @Override
    public void checkUserExist(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id: " + userId + " does not exist");
        }
    }

    @Override
    @Transactional
    public UserCommentAdminDto updateUserBan(Long userId, LocalDateTime banDate) {
        User user = findById(userId);
        return banDate != null ? banUser(user, banDate) : unbanUser(user);
    }

    private static UserCommentAdminDto unbanUser(User user) {
        if (!user.isBanned()) {
            throw new ConflictException("User not banned");
        }

        log.info("Пользователь разблокирован:{}", user);
        user.setBannedUntil(null);
        return UserMapper.toUserCommentAdminDto(user);
    }

    private static UserCommentAdminDto banUser(User user, LocalDateTime banDate) {
        if (user.isBanned()) {
            throw new ConflictException("User already banned");
        }

        log.info("Пользователь получил блокировку до {}: {}", banDate, user);
        user.setBannedUntil(banDate);
        return UserMapper.toUserCommentAdminDto(user);
    }

    @Override
    @Transactional
    public UserFullDto addAdminWarning(Long userId) {
        User user = findById(userId);

        if (user.isWarningsLimitExceeded()) {
            log.info("Пользователь заблокирован за превышение лимита предупреждений: {}", user);
            user.setAdminWarnings(0);
            user.setBannedUntil(LocalDateTime.now().plusMonths(6));
        } else {
            log.info("Пользователь получил предупреждение: {}", user);
            user.setAdminWarnings(user.getAdminWarnings() + 1);
        }

        return UserMapper.toUserFullDto(user);
    }

    @Override
    @Transactional
    public UserFullDto incrementCommentsCount(Long userId) {
        User user = findById(userId);
        user.setCommentsCount(user.getCommentsCount() + 1);
        updateRank(user);
        return UserMapper.toUserFullDto(user);
    }

    @Override
    @Transactional
    public UserFullDto decrementCommentsCount(Long userId) {
        User user = findById(userId);
        user.setCommentsCount(Math.max(0, user.getCommentsCount() - 1));
        updateRank(user);
        return UserMapper.toUserFullDto(user);
    }

    @Override
    public List<User> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllByIdIn(ids);
    }

    private void updateRank(User user) {
        int count = user.getCommentsCount();

        if (count == 1 && user.getRank() == CommentsRank.REGULAR) {
            user.setRank(CommentsRank.NOVICE);
        }

        if (count == 2) {
            user.setRank(CommentsRank.REGULAR);
        }

        if (count > 2 && user.getRank() == CommentsRank.REGULAR) {
            user.setRank(CommentsRank.VETERAN);
        }
    }
}
