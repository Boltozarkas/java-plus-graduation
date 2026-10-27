package ru.practicum.entity.comment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findAllByAuthorId(Long authorId);

    List<Comment> findAllByEventId(Long eventId);

    List<Comment> findAllByTextContainsIgnoreCase(String text, Pageable pageable);
}