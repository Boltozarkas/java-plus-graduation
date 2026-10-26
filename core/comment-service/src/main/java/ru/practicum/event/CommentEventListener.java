package ru.practicum.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.practicum.feign.user.UserClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentEventListener {

    private final UserClient userClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleCommentCountUpdate(CommentCountUpdateEvent event) {
        try {
            if (event.isIncrement()) {
                userClient.incrementCommentsCount(event.getUserId());
                log.info("Счетчик комментариев увеличен для пользователя {}", event.getUserId());
            } else {
                userClient.decrementCommentsCount(event.getUserId());
                log.info("Счетчик комментариев уменьшен для пользователя {}", event.getUserId());
            }
        } catch (Exception e) {
            log.error("Ошибка обновления счетчика комментариев для пользователя {}. " +
                    "Требуется повторная обработка.", event.getUserId(), e);
        }
    }
}