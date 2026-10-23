package ru.practicum.entity.event;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>,
        QuerydslPredicateExecutor<Event> {

    @EntityGraph(attributePaths = {"category"})
    Optional<Event> findWithRelationsById(Long id);

    @EntityGraph(attributePaths = {"category"})
    Optional<Event> findByIdAndState(Long id, EventState state);

    @EntityGraph(attributePaths = {"category"})
    List<Event> findAllByInitiatorId(Long initiatorId, org.springframework.data.domain.Pageable pageable);

    boolean existsByCategoryId(Long categoryId);
}