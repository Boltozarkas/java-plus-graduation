package ru.practicum.aggregator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {
    private final KafkaConsumer<Long, SpecificRecordBase> consumer;
    private final KafkaProducer<String, SpecificRecordBase> producer;
    private final SimilarityService similarityService;

    @Value("${kafka.topics.user-actions}")
    private String inputTopic;

    @Value("${kafka.topics.events-similarity}")
    private String outputTopic;

    private volatile boolean running = true;

    public void start() {
        try {
            consumer.subscribe(List.of(inputTopic));
            log.info("Aggregator started, subscribed to {}", inputTopic);

            while (running) {
                ConsumerRecords<Long, SpecificRecordBase> records = consumer.poll(Duration.ofMillis(1000));
                if (records.isEmpty()) {
                    continue;
                }

                // Собираем futures всех отправок текущей пачки
                List<Future<RecordMetadata>> futures = new ArrayList<>();

                for (ConsumerRecord<Long, SpecificRecordBase> record : records) {
                    UserActionAvro action = (UserActionAvro) record.value();
                    List<EventSimilarityAvro> similarities = similarityService.processAction(action);

                    for (EventSimilarityAvro sim : similarities) {
                        ProducerRecord<String, SpecificRecordBase> pr = new ProducerRecord<>(
                                outputTopic,
                                null,
                                sim.getTimestamp().toEpochMilli(),
                                String.valueOf(sim.getEventA()),
                                sim
                        );
                        // Асинхронная отправка — не блокирует цикл
                        futures.add(producer.send(pr));
                        log.debug("Sent similarity: {} and {} score {}",
                                sim.getEventA(), sim.getEventB(), sim.getScore());
                    }
                }

                // Блокируемся ОДИН раз: ждём подтверждения всех отправок пачки
                producer.flush();

                // Проверяем результаты; если хоть одна отправка упала — не коммитим offset
                for (Future<RecordMetadata> future : futures) {
                    try {
                        future.get(); // после flush() уже done, вернётся мгновенно
                    } catch (ExecutionException e) {
                        log.error("Failed to send similarity to Kafka", e.getCause());
                        throw new RuntimeException("Failed to send similarity to Kafka", e.getCause());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for Kafka send", e);
                    }
                }

                // Все отправки подтверждены — коммитим offset
                consumer.commitSync();
            }
        } catch (WakeupException e) {
            if (running) {
                log.error("Unexpected WakeupException in AggregationStarter", e);
                throw e;
            }
            log.info("AggregationStarter stopped via wakeup()");
        } catch (Exception e) {
            log.error("Error in AggregationStarter", e);
        } finally {
            try {
                consumer.close();
                log.info("Aggregator consumer closed");
            } catch (Exception e) {
                log.error("Error closing consumer", e);
            }
            try {
                producer.close();
                log.info("Aggregator producer closed");
            } catch (Exception e) {
                log.error("Error closing producer", e);
            }
        }
    }

    public void shutdown() {
        running = false;
        consumer.wakeup();
    }
}