package ru.practicum.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.UserActionAvro;

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
                avro.getEventId(), // <-- передаем Long
                avro
        );

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Failed to send user action", exception);
            } else {
                log.info("User action sent: eventId={}, userId={}, type={}",
                        avro.getEventId(), avro.getUserId(), avro.getActionType());
            }
        });
    }
}