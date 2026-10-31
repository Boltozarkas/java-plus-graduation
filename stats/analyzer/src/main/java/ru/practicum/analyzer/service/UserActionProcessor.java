package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.analyzer.model.UserAction;
import ru.practicum.analyzer.repository.UserActionRepository;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProcessor implements Runnable {
    private final KafkaConsumer<String, SpecificRecordBase> userActionConsumer;
    private final UserActionRepository repository;

    @Value("${kafka.topics.user-actions}")
    private String topic;

    @Override
    public void run() {
        try {
            userActionConsumer.subscribe(List.of(topic));
            while (true) {
                var records = userActionConsumer.poll(Duration.ofMillis(1000));
                for (var record : records) {
                    UserActionAvro avro = (UserActionAvro) record.value();
                    processAction(avro);
                }
                userActionConsumer.commitSync();
            }
        } catch (Exception e) {
            log.error("Error in UserActionProcessor", e);
        }
    }

    @Transactional
    protected void processAction(UserActionAvro avro) {
        double newWeight = getWeight(avro);
        LocalDateTime newTimestamp = LocalDateTime.ofInstant(avro.getTimestamp(), ZoneOffset.UTC);

        var existing = repository.findByUserIdAndEventId(avro.getUserId(), avro.getEventId());

        if (existing.isPresent()) {
            UserAction action = existing.get();
            // Обновляем только если новый вес больше
            if (newWeight > action.getWeight()) {
                action.setWeight(newWeight);
                action.setActionType(avro.getActionType().name());
                action.setTimestamp(newTimestamp);
                repository.save(action);
                log.info("Updated action: user {}, event {}, weight {}", avro.getUserId(), avro.getEventId(), newWeight);
            }
        } else {
            UserAction entity = UserAction.builder()
                    .userId(avro.getUserId())
                    .eventId(avro.getEventId())
                    .weight(newWeight)
                    .actionType(avro.getActionType().name())
                    .timestamp(newTimestamp)
                    .build();
            repository.save(entity);
            log.info("Saved new action: user {}, event {}", avro.getUserId(), avro.getEventId());
        }
    }

    private double getWeight(UserActionAvro avro) {
        return switch (avro.getActionType()) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }
}