package ru.practicum.entity.user;

import lombok.experimental.UtilityClass;
import ru.practicum.dto.comment.UserCommentAdminDto;
import ru.practicum.dto.user.NewUserRequest;
import ru.practicum.dto.user.UserDto;
import ru.practicum.dto.user.UserFullDto;
import ru.practicum.dto.user.UserShortDto;

import java.time.format.DateTimeFormatter;
import java.util.List;

@UtilityClass
public class UserMapper {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static User toUser(final NewUserRequest newUserRequest) {
        return User.builder()
                .mail(newUserRequest.getEmail())
                .name(newUserRequest.getName())
                .rank(CommentsRank.NOVICE)
                .build();
    }

    static UserDto toUserDto(final User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getMail())
                .name(user.getName())
                .build();
    }

    static List<UserDto> toUserDtos(final List<User> users) {
        return users.stream()
                .map(UserMapper::toUserDto)
                .toList();
    }

    public static UserShortDto toShortDto(final User user) {
        return UserShortDto.builder()
                .name(user.getName())
                .id(user.getId())
                .build();
    }

    public static UserCommentAdminDto toUserCommentAdminDto(User user) {
        return UserCommentAdminDto.builder()
                .authorId(user.getId())
                .authorName(user.getName())
                .adminWarnCount(user.getAdminWarnings())
                .bannedUntil(user.getBannedUntil() != null
                        ? user.getBannedUntil().format(formatter)
                        : null)
                .build();
    }

    public static UserFullDto toUserFullDto(User user) {
        return UserFullDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getMail())
                .rank(user.getRank().name())
                .commentsCount(user.getCommentsCount())
                .adminWarnings(user.getAdminWarnings())
                .bannedUntil(user.getBannedUntil() != null
                        ? user.getBannedUntil().format(formatter) : null)
                .banned(user.isBanned())
                .build();
    }
}