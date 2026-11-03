package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSimilarityService {
    private final EventSimilarityRepository repository;

    @Transactional
    public void save(EventSimilarityAvro avro) {
        LocalDateTime newTimestamp = LocalDateTime.ofInstant(avro.getTimestamp(), ZoneOffset.UTC);

        Optional<EventSimilarity> existing = repository.findByEventAAndEventB(
                avro.getEventA(), avro.getEventB());

        if (existing.isPresent()) {
            EventSimilarity entity = existing.get();
            entity.setScore(avro.getScore());
            entity.setTimestamp(newTimestamp);
            repository.save(entity);
            log.info("Updated similarity: {} - {} = {}", avro.getEventA(), avro.getEventB(), avro.getScore());
        } else {
            EventSimilarity entity = EventSimilarity.builder()
                    .eventA(avro.getEventA())
                    .eventB(avro.getEventB())
                    .score(avro.getScore())
                    .timestamp(newTimestamp)
                    .build();
            repository.save(entity);
            log.info("Saved new similarity: {} - {} = {}", avro.getEventA(), avro.getEventB(), avro.getScore());
        }
    }
}