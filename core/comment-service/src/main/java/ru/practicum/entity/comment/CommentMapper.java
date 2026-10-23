package ru.practicum.entity.comment;

import lombok.experimental.UtilityClass;
import ru.practicum.dto.comment.CommentDto;
import ru.practicum.dto.comment.LogComment;
import ru.practicum.dto.comment.NewCommentDto;
import ru.practicum.dto.user.UserShortDto;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

@UtilityClass
public class CommentMapper {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static Comment toComment(NewCommentDto dto, Long authorId, Long eventId) {
        return Comment.builder()
                .text(dto.getText())
                .authorId(authorId)
                .eventId(eventId)
                .build();
    }

    public static CommentDto toCommentDto(Comment comment, UserShortDto author) {
        return CommentDto.builder()
                .id(comment.getId())
                .authorId(comment.getAuthorId())
                .eventId(comment.getEventId())
                .authorName(author != null ? author.getName() : null)
                .text(comment.getText())
                .created(comment.getCreated().format(formatter))
                .updated(comment.getUpdated() != null ? comment.getUpdated().format(formatter) : null)
                .build();
    }

    public static List<CommentDto> toCommentDto(List<Comment> comments,
                                                Function<Long, UserShortDto> authorResolver) {
        return comments.stream()
                .map(c -> toCommentDto(c, authorResolver.apply(c.getAuthorId())))
                .toList();
    }

    public static LogComment toLogComment(CommentDto comment) {
        return LogComment.builder()
                .id(comment.getId())
                .authorId(comment.getAuthorId())
                .eventId(comment.getEventId())
                .authorName(comment.getAuthorName())
                .text(stringBuilder(comment.getText()))
                .created(comment.getCreated())
                .updated(comment.getUpdated())
                .build();
    }

    private static String stringBuilder(String string) {
        if (string == null) {
            return null;
        }
        return string.length() > 10 ? string.substring(0, 10) + "..." : string;
    }
}