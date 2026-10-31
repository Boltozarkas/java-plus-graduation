package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.model.UserAction;

import java.util.List;
import java.util.Optional;

public interface UserActionRepository extends JpaRepository<UserAction, Long> {

    List<UserAction> findByUserIdOrderByTimestampDesc(Long userId);

    // Находим конкретное взаимодействие
    Optional<UserAction> findByUserIdAndEventId(Long userId, Long eventId);

    @Query("select ua from UserAction ua where ua.userId = :userId and ua.eventId in :eventIds")
    List<UserAction> findActionsByUserIdAndEventIdIn(@Param("userId") Long userId, @Param("eventIds") List<Long> eventIds);

    // Для получения суммы весов (для метода GetInteractionsCount)
    @Query("select coalesce(sum(ua.weight), 0.0) from UserAction ua where ua.eventId = :eventId")
    Double sumWeightByEventId(@Param("eventId") Long eventId);
}