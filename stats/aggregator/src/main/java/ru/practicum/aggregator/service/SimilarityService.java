package ru.practicum.aggregator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
public class SimilarityService {
    private static final double WEIGHT_VIEW = 0.4;
    private static final double WEIGHT_REGISTER = 0.8;
    private static final double WEIGHT_LIKE = 1.0;

    // eventId -> (userId -> maxWeight)
    private final Map<Long, Map<Long, Double>> eventUserWeights = new HashMap<>();
    // eventId -> sum of weights
    private final Map<Long, Double> eventTotalWeights = new HashMap<>();
    // eventA -> (eventB -> S_min(A, B))
    private final Map<Long, Map<Long, Double>> minWeightsSums = new HashMap<>();

    public List<EventSimilarityAvro> processAction(UserActionAvro action) {
        long userId = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = getWeight(action.getActionType());

        Map<Long, Double> userWeights = eventUserWeights.computeIfAbsent(eventId, k -> new HashMap<>());
        Double oldWeight = userWeights.get(userId);

        if (oldWeight != null && oldWeight >= newWeight) {
            return Collections.emptyList();
        }

        double oldW = oldWeight == null ? 0.0 : oldWeight;
        double deltaW = newWeight - oldW;
        userWeights.put(userId, newWeight);
        eventTotalWeights.merge(eventId, deltaW, Double::sum);

        List<EventSimilarityAvro> results = new ArrayList<>();

        for (Long otherEventId : eventUserWeights.keySet()) {
            if (otherEventId == eventId) continue;
            Map<Long, Double> otherWeights = eventUserWeights.get(otherEventId);
            if (!otherWeights.containsKey(userId)) continue;

            double otherW = otherWeights.get(userId);
            double oldMin = Math.min(oldW, otherW);
            double newMin = Math.min(newWeight, otherW);
            double deltaMin = newMin - oldMin;

            long first = Math.min(eventId, otherEventId);
            long second = Math.max(eventId, otherEventId);

            // Обновляем S_min, только если он изменился
            if (deltaMin != 0) {
                minWeightsSums.computeIfAbsent(first, k -> new HashMap<>())
                        .merge(second, deltaMin, Double::sum);
            }

            // ВСЕГДА пересчитываем score и отправляем, если S_A или S_B изменились
            double sMin = minWeightsSums.getOrDefault(first, Collections.emptyMap()).getOrDefault(second, 0.0);
            double sA = eventTotalWeights.getOrDefault(first, 0.0);
            double sB = eventTotalWeights.getOrDefault(second, 0.0);

            if (sA == 0 || sB == 0) continue;

            double score = sMin / Math.sqrt(sA * sB);

            // Округляем до 2-х знаков после запятой
            double roundedScore = BigDecimal.valueOf(score)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            results.add(EventSimilarityAvro.newBuilder()
                    .setEventA(first)
                    .setEventB(second)
                    .setScore(roundedScore) // <-- используем округленное значение
                    .setTimestamp(action.getTimestamp())
                    .build());
        }

        return results;
    }

    private double getWeight(ActionTypeAvro type) {
        return switch (type) {
            case VIEW -> WEIGHT_VIEW;
            case REGISTER -> WEIGHT_REGISTER;
            case LIKE -> WEIGHT_LIKE;
        };
    }
}