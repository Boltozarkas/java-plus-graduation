package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityProcessor implements Runnable {
    private final KafkaConsumer<String, SpecificRecordBase> similarityConsumer;
    private final EventSimilarityRepository repository;

    @Value("${kafka.topics.events-similarity}")
    private String topic;

    @Override
    public void run() {
        try {
            similarityConsumer.subscribe(List.of(topic));
            while (true) {
                var records = similarityConsumer.poll(Duration.ofMillis(1000));
                for (var record : records) {
                    EventSimilarityAvro avro = (EventSimilarityAvro) record.value();
                    EventSimilarity entity = EventSimilarity.builder()
                            .eventA(avro.getEventA())
                            .eventB(avro.getEventB())
                            .score(avro.getScore())
                            .timestamp(LocalDateTime.ofInstant(avro.getTimestamp(), ZoneOffset.UTC))
                            .build();
                    repository.save(entity);
                    log.info("Saved similarity: {} - {} = {}", avro.getEventA(), avro.getEventB(), avro.getScore());
                }
                similarityConsumer.commitSync();
            }
        } catch (Exception e) {
            log.error("Error in SimilarityProcessor", e);
        }
    }
}