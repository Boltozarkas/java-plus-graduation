package ru.practicum.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CommentWarningEvent extends ApplicationEvent {
    private final Long authorId;

    public CommentWarningEvent(Object source, Long authorId) {
        super(source);
        this.authorId = authorId;
    }
}