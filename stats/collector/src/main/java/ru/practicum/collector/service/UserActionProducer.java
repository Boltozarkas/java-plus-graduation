package ru.practicum.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActionProducer {
    private final KafkaProducer<Long, SpecificRecordBase> producer;

    @Value("${kafka.topics.user-actions}")
    private String topic;

    public void sendUserAction(UserActionAvro avro) {
        ProducerRecord<Long, SpecificRecordBase> record = new ProducerRecord<>(
                topic,
                null,
                avro.getTimestamp().toEpochMilli(),
                avro.getEventId(), // Long
                avro
        );

        try {
            RecordMetadata metadata = producer.send(record).get();
            log.info("User action sent: eventId={}, userId={}, type={}, topic={}, partition={}, offset={}",
                    avro.getEventId(), avro.getUserId(), avro.getActionType(),
                    metadata.topic(), metadata.partition(), metadata.offset());
        } catch (ExecutionException e) {
            log.error("Failed to send user action to Kafka: eventId={}, userId={}",
                    avro.getEventId(), avro.getUserId(), e.getCause());
            throw new RuntimeException("Failed to send user action to Kafka", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending user action to Kafka", e);
        }
    }
}