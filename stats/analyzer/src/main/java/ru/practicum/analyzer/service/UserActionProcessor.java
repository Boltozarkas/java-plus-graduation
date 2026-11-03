package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProcessor implements Runnable {
    private final KafkaConsumer<Long, SpecificRecordBase> userActionConsumer;
    private final UserActionService userActionService;

    @Value("${kafka.topics.user-actions}")
    private String topic;

    private volatile boolean running = true;

    @Override
    public void run() {
        try {
            userActionConsumer.subscribe(List.of(topic));
            while (running) {
                var records = userActionConsumer.poll(Duration.ofMillis(1000));
                if (records.isEmpty()) {
                    continue;
                }
                // Обрабатываем всю пачку; если хоть одна запись упадёт — не коммитим offset
                for (var record : records) {
                    UserActionAvro avro = (UserActionAvro) record.value();
                    userActionService.save(avro); // ← публичный @Transactional через прокси
                }
                // Только после успешной обработки всех записей пачки
                userActionConsumer.commitSync();
            }
        } catch (WakeupException e) {
            if (running) {
                log.error("Unexpected WakeupException in UserActionProcessor", e);
                throw e;
            }
            log.info("UserActionProcessor stopped via wakeup()");
        } catch (Exception e) {
            log.error("Error in UserActionProcessor", e);
        } finally {
            try {
                userActionConsumer.close();
                log.info("UserActionConsumer closed");
            } catch (Exception e) {
                log.error("Error closing userActionConsumer", e);
            }
        }
    }

    public void shutdown() {
        running = false;
        userActionConsumer.wakeup();
    }
}