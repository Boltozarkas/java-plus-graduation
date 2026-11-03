package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.model.UserAction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserActionRepository extends JpaRepository<UserAction, Long> {

    List<UserAction> findByUserIdOrderByTimestampDesc(Long userId);

    Optional<UserAction> findByUserIdAndEventId(Long userId, Long eventId);

    @Query("select ua from UserAction ua where ua.userId = :userId and ua.eventId in :eventIds")
    List<UserAction> findActionsByUserIdAndEventIdIn(@Param("userId") Long userId,
                                                     @Param("eventIds") List<Long> eventIds);

    // Новый батчевый метод: суммы весов по всем eventId одним запросом
    @Query("select ua.eventId, coalesce(sum(ua.weight), 0.0) " +
            "from UserAction ua " +
            "where ua.eventId in :eventIds " +
            "group by ua.eventId")
    List<Object[]> sumWeightsByEventIds(@Param("eventIds") Collection<Long> eventIds);

    // Старый метод удалён — использовался только в getInteractionsCount
}