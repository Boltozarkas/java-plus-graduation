package ru.practicum.aggregator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {
    private final KafkaConsumer<String, SpecificRecordBase> consumer;
    private final KafkaProducer<String, SpecificRecordBase> producer;
    private final SimilarityService similarityService;

    @Value("${kafka.topics.user-actions}")
    private String inputTopic;

    @Value("${kafka.topics.events-similarity}")
    private String outputTopic;

    public void start() {
        try {
            consumer.subscribe(List.of(inputTopic));
            log.info("Aggregator started, subscribed to {}", inputTopic);

            while (true) {
                ConsumerRecords<String, SpecificRecordBase> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, SpecificRecordBase> record : records) {
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
                        producer.send(pr);
                        log.debug("Sent similarity: {} and {} score {}", sim.getEventA(), sim.getEventB(), sim.getScore());
                    }
                }
                consumer.commitSync();
            }
        } catch (Exception e) {
            log.error("Error in AggregationStarter", e);
        } finally {
            consumer.close();
            producer.close();
        }
    }

    public void shutdown() {
        consumer.wakeup();
    }
}