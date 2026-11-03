package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityProcessor implements Runnable {
    private final KafkaConsumer<String, SpecificRecordBase> similarityConsumer;
    private final EventSimilarityService eventSimilarityService;

    @Value("${kafka.topics.events-similarity}")
    private String topic;

    private volatile boolean running = true;

    @Override
    public void run() {
        try {
            similarityConsumer.subscribe(List.of(topic));
            while (running) {
                var records = similarityConsumer.poll(Duration.ofMillis(1000));
                if (records.isEmpty()) {
                    continue;
                }
                for (var record : records) {
                    EventSimilarityAvro avro = (EventSimilarityAvro) record.value();
                    eventSimilarityService.save(avro); // ← публичный @Transactional через прокси
                }
                similarityConsumer.commitSync();
            }
        } catch (WakeupException e) {
            if (running) {
                log.error("Unexpected WakeupException in SimilarityProcessor", e);
                throw e;
            }
            log.info("SimilarityProcessor stopped via wakeup()");
        } catch (Exception e) {
            log.error("Error in SimilarityProcessor", e);
        } finally {
            try {
                similarityConsumer.close();
                log.info("SimilarityConsumer closed");
            } catch (Exception e) {
                log.error("Error closing similarityConsumer", e);
            }
        }
    }

    public void shutdown() {
        running = false;
        similarityConsumer.wakeup();
    }
}