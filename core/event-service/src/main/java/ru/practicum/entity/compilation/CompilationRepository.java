package ru.practicum.entity.compilation;

import com.querydsl.core.types.Predicate;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

import java.util.Optional;

public interface CompilationRepository extends JpaRepository<Compilation, Long>,
        QuerydslPredicateExecutor<Compilation> {

    boolean existsByTitle(String title);

    @NonNull
    @EntityGraph(attributePaths = {"events", "events.category"})
    Page<Compilation> findAll(@NonNull Pageable pageable);

    @NonNull
    @EntityGraph(attributePaths = {"events", "events.category"})
    Page<Compilation> findAll(@NonNull Predicate predicate, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"events", "events.category"})
    Optional<Compilation> findWithEventsById(Long id);
}