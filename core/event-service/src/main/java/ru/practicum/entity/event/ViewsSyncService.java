package ru.practicum.entity.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViewsSyncService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BATCH_SIZE = 500;

    private final EventRepository eventRepository;
    //    private final StatClient statClient;
    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Начальная синхронизация views из stats...");
        syncAll();
    }

    @Scheduled(fixedDelayString = "${event.views.sync.delay:300000}")
    public void scheduledSync() {
        syncAll();
    }

    public void syncAll() {
        try {
            List<Long> allIds = eventRepository.findAllIds();
            if (allIds.isEmpty()) {
                return;
            }

            Map<Long, Long> statsByEventId = fetchStats(allIds);
            if (statsByEventId.isEmpty()) {
                return;
            }

            for (int i = 0; i < allIds.size(); i += BATCH_SIZE) {
                List<Long> batch = allIds.subList(i, Math.min(i + BATCH_SIZE, allIds.size()));
                jdbcTemplate.batchUpdate(
                        "UPDATE events SET views = ? WHERE id = ?",
                        new BatchPreparedStatementSetter() {
                            @Override
                            public void setValues(PreparedStatement ps, int idx) throws SQLException {
                                Long id = batch.get(idx);
                                ps.setLong(1, statsByEventId.getOrDefault(id, 0L));
                                ps.setLong(2, id);
                            }

                            @Override
                            public int getBatchSize() {
                                return batch.size();
                            }
                        });
            }
            log.info("Синхронизация views завершена: обработано {} событий", allIds.size());
        } catch (Exception e) {
            log.error("Ошибка синхронизации views из stats", e);
        }
    }

    private Map<Long, Long> fetchStats(List<Long> eventIds) {
        List<String> uris = eventIds.stream()
                .map(id -> "/events/" + id)
                .toList();

        LocalDateTime start = LocalDateTime.now().minusYears(5);
        LocalDateTime end = LocalDateTime.now().plusYears(1);

//        List<ViewStats> stats = statClient.getViewStats(new StatsRequest(
//                start.format(FORMATTER),
//                end.format(FORMATTER),
//                uris,
//                true));
//
//        if (stats == null || stats.isEmpty()) {
//            log.warn("Stats вернул пустой ответ — пропускаем синхронизацию views");
//            return Map.of();
//        }
//
//        return stats.stream()
//                .collect(Collectors.toMap(
//                        vs -> Long.parseLong(vs.getUri().substring("/events/".length())),
//                        ViewStats::getHits,
//                        (a, b) -> a));
        return Map.of();
    }
}