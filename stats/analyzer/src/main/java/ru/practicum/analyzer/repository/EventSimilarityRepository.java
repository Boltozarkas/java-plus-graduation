package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.model.EventSimilarity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {

    List<EventSimilarity> findByEventAOrEventBOrderByScoreDesc(Long eventA, Long eventB);

    @Query("select es from EventSimilarity es " +
            "where es.eventA in :eventIds or es.eventB in :eventIds " +
            "order by es.score desc")
    List<EventSimilarity> findAllByEventIds(@Param("eventIds") Collection<Long> eventIds);

    // Новый метод: поиск по паре (eventA, eventB) — для upsert-логики
    Optional<EventSimilarity> findByEventAAndEventB(Long eventA, Long eventB);
}