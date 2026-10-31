package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.analyzer.model.EventSimilarity;

import java.util.List;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {
    List<EventSimilarity> findByEventAOrEventBOrderByScoreDesc(Long eventA, Long eventB);
}