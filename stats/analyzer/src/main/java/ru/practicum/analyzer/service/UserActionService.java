package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.analyzer.model.UserAction;
import ru.practicum.analyzer.repository.UserActionRepository;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActionService {
    private final UserActionRepository repository;

    @Transactional
    public void save(UserActionAvro avro) {
        double newWeight = getWeight(avro);
        LocalDateTime newTimestamp = LocalDateTime.ofInstant(avro.getTimestamp(), ZoneOffset.UTC);

        var existing = repository.findByUserIdAndEventId(avro.getUserId(), avro.getEventId());

        if (existing.isPresent()) {
            UserAction action = existing.get();
            if (newWeight > action.getWeight()) {
                action.setWeight(newWeight);
                action.setActionType(avro.getActionType().name());
                action.setTimestamp(newTimestamp);
                repository.save(action);
                log.info("Updated action: user {}, event {}, weight {}",
                        avro.getUserId(), avro.getEventId(), newWeight);
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