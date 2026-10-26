package ru.practicum.entity.comment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.practicum.dto.comment.CommentDto;
import ru.practicum.dto.comment.NewCommentDto;
import ru.practicum.dto.comment.UpdateCommentDto;
import ru.practicum.dto.comment.UserCommentAdminDto;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.user.UserFullDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.event.CommentCountUpdateEvent;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.feign.event.EventClient;
import ru.practicum.feign.user.UserClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
// @Transactional(readOnly = true) с класса убран
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public CommentDto createComment(NewCommentDto newCommentDto, Long userId) {
        UserFullDto fullUser = userClient.findFullById(userId);
        if (fullUser.isBanned()) {
            throw new ConflictException("You have been banned!");
        }

        UserShortDto user = new UserShortDto(fullUser.getId(), fullUser.getName());
        EventFullDto event = eventClient.findById(newCommentDto.getEventId());

        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictException("Cannot comment on unpublished event.");
        }

        // Сохраняем в транзакции и публикуем событие ВНУТРИ транзакции
        Comment comment = transactionTemplate.execute(status -> {
            Comment newComment = CommentMapper.toComment(newCommentDto, user.getId(), event.getId());
            Comment saved = commentRepository.save(newComment);
            // Публикуем событие до коммита, AFTER_COMMIT сработает после успешного сохранения
            eventPublisher.publishEvent(new CommentCountUpdateEvent(this, userId, true));
            return saved;
        });

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
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = getById(commentId);
        checkAuthor(comment, userId);

        Long authorId = comment.getAuthorId();

        transactionTemplate.execute(status -> {
            commentRepository.delete(comment);
            eventPublisher.publishEvent(new CommentCountUpdateEvent(this, authorId, false));
            return null;
        });

        log.info("Комментарий id: {} удален пользователем id: {}", commentId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getComments(Long userId) {
        userClient.findById(userId);
        List<Comment> comments = commentRepository.findAllByAuthorId(userId);
        return mapWithAuthors(comments);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getEventComments(Long eventId) {
        if (!eventClient.existsById(eventId)) {
            throw new NotFoundException("Event with id " + eventId + " not found");
        }
        List<Comment> comments = commentRepository.findAllByEventId(eventId);
        return mapWithAuthors(comments);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> searchComments(String text, Pageable pageable) {
        List<Comment> comments = commentRepository.findAllByTextContainsIgnoreCase(text, pageable);
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }
        return mapWithAuthors(comments);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentDto getComment(Long commentId) {
        Comment comment = getById(commentId);
        UserShortDto user = userClient.findById(comment.getAuthorId());
        return CommentMapper.toCommentDto(comment, user);
    }

    @Override
    @Transactional(readOnly = true)
    public Comment getById(Long id) {
        return commentRepository.findById(id).orElseThrow(
                () -> new NotFoundException("Comment not found with id: " + id));
    }

    @Override
    public void adminDelete(Long commentId) {
        Comment comment = getById(commentId);
        CommentDto dto = CommentMapper.toCommentDto(comment, null);
        Long authorId = comment.getAuthorId();

        transactionTemplate.execute(status -> {
            commentRepository.delete(comment);
            eventPublisher.publishEvent(new CommentCountUpdateEvent(this, authorId, false));
            return null;
        });

        log.info("Комментарий удален администратором: {}", CommentMapper.toLogComment(dto));
    }

    @Override
    public UserCommentAdminDto giveWarning(Long commentId) {
        Comment comment = getById(commentId);
        if (comment.isAdminWarning()) {
            throw new ConflictException(
                    String.format("Comment with id %d already received a warning", commentId));
        }

        transactionTemplate.execute(status -> {
            comment.setAdminWarning(true);
            return commentRepository.save(comment);
        });

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
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> authorIds = comments.stream()
                .map(Comment::getAuthorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (authorIds.isEmpty()) {
            return CommentMapper.toCommentDto(comments, Collections.emptyMap());
        }

        Map<Long, UserShortDto> authorsMap = userClient.findByIds(authorIds).stream()
                .collect(Collectors.toMap(UserShortDto::getId, u -> u));

        return CommentMapper.toCommentDto(comments, authorsMap);
    }

    private void checkAuthor(Comment comment, Long userId) {
        if (!comment.getAuthorId().equals(userId)) {
            throw new ConflictException("It's not your own comment!");
        }
    }
}