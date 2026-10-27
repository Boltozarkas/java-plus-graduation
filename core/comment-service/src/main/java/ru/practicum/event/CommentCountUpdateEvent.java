package ru.practicum.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CommentCountUpdateEvent extends ApplicationEvent {
    private final Long userId;
    private final boolean increment;

    public CommentCountUpdateEvent(Object source, Long userId, boolean increment) {
        super(source);
        this.userId = userId;
        this.increment = increment;
    }
}