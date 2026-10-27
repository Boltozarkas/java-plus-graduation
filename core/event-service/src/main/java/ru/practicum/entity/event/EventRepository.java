package ru.practicum.entity.event;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>,
        QuerydslPredicateExecutor<Event>,
        EventRepositoryCustom {

    @EntityGraph(attributePaths = {"category"})
    Optional<Event> findWithRelationsById(Long id);

    @EntityGraph(attributePaths = {"category"})
    Optional<Event> findByIdAndState(Long id, EventState state);

    @EntityGraph(attributePaths = {"category"})
    List<Event> findAllByInitiatorId(Long initiatorId, org.springframework.data.domain.Pageable pageable);

    boolean existsByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = {"category"})
    @Query("select e from Event e where e.id in :ids")
    List<Event> findAllByIdInWithCategory(@Param("ids") Collection<Long> ids);

    @Query("select e.id from Event e")
    List<Long> findAllIds();
}