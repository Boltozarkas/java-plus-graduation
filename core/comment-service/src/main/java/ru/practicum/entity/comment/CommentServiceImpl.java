package ru.practicum.entity.comment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.comment.CommentDto;
import ru.practicum.dto.comment.NewCommentDto;
import ru.practicum.dto.comment.UpdateCommentDto;
import ru.practicum.dto.comment.UserCommentAdminDto;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.user.UserFullDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.event.EventClient;
import ru.practicum.feign.user.UserClient;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserClient userClient;
    private final EventClient eventClient;

    @Override
    @Transactional
    public CommentDto createComment(NewCommentDto newCommentDto, Long userId) {
        UserFullDto fullUser = userClient.findFullById(userId);
        if (fullUser.isBanned()) {
            throw new ConflictException("You have been banned!");
        }

        UserShortDto user = userClient.findById(userId);
        EventFullDto event = eventClient.findById(newCommentDto.getEventId());

        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictException("Cannot comment on unpublished event.");
        }

        Comment comment = commentRepository.save(
                CommentMapper.toComment(newCommentDto, user.getId(), event.getId()));
        userClient.incrementCommentsCount(userId);
        CommentDto commentDto = CommentMapper.toCommentDto(comment, user);
        log.info("Новый комментарий на событие: {}", CommentMapper.toLogComment(commentDto));
        return commentDto;
    }

    @Override
    @Transactional
    public CommentDto updateComment(UpdateCommentDto updateCommentDto) {
        Comment comment = getById(updateCommentDto.getCommentId());
        UserShortDto user = userClient.findById(updateCommentDto.getUserId());

        checkAuthor(comment, user.getId());

        comment.setText(updateCommentDto.getText());
        CommentDto commentDto = CommentMapper.toCommentDto(comment, user);
        log.info("Обновлен комментарий: {}", CommentMapper.toLogComment(commentDto));

        return commentDto;
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = getById(commentId);
        checkAuthor(comment, userId);

        Long authorId = comment.getAuthorId();
        commentRepository.delete(comment);
        userClient.decrementCommentsCount(authorId);

        log.info("Комментарий id: {} удален пользователем id: {}", commentId, userId);
    }

    @Override
    public List<CommentDto> getComments(Long userId) {
        userClient.findById(userId);
        return mapWithAuthors(commentRepository.findAllByAuthorId(userId));
    }

    @Override
    public List<CommentDto> getEventComments(Long eventId) {
        eventClient.existsById(eventId);
        return mapWithAuthors(commentRepository.findAllByEventId(eventId));
    }

    @Override
    public CommentDto getComment(Long commentId) {
        Comment comment = getById(commentId);
        UserShortDto user = userClient.findById(comment.getAuthorId());
        return CommentMapper.toCommentDto(comment, user);
    }

    @Override
    public List<CommentDto> searchComments(String text, Pageable pageable) {
        List<Comment> comments = commentRepository.findAllByTextContainsIgnoreCase(text, pageable);
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        return mapWithAuthors(comments);
    }

    @Override
    public Comment getById(Long id) {
        return commentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Comment not found with id: " + id));
    }

    @Override
    @Transactional
    public void adminDelete(Long commentId) {
        Comment comment = getById(commentId);
        CommentDto dto = CommentMapper.toCommentDto(comment, null);
        Long authorId = comment.getAuthorId();
        commentRepository.delete(comment);
        userClient.decrementCommentsCount(authorId);
        log.info("Комментарий удален администратором: {}", CommentMapper.toLogComment(dto));
    }

    @Override
    @Transactional
    public UserCommentAdminDto giveWarning(Long commentId) {
        Comment comment = getById(commentId);

        if (comment.isAdminWarning()) {
            throw new ConflictException(
                    String.format("Comment with id %d already received a warning", commentId));
        }

        comment.setAdminWarning(true);

        UserFullDto author = userClient.giveWarning(comment.getAuthorId());

        log.info("Комментарий id: {} получил предупреждение. Автор: {}", commentId, author);

        return UserCommentAdminDto.builder()
                .authorId(author.getId())
                .authorName(author.getName())
                .adminWarnCount(author.getAdminWarnings())
                .bannedUntil(author.getBannedUntil())
                .build();
    }

    private List<CommentDto> mapWithAuthors(List<Comment> comments) {
        Function<Long, UserShortDto> authorResolver = userClient::findById;
        return CommentMapper.toCommentDto(comments, authorResolver);
    }

    private void checkAuthor(Comment comment, Long userId) {
        if (!comment.getAuthorId().equals(userId)) {
            throw new ConflictException("It's not your own comment!");
        }
    }
}