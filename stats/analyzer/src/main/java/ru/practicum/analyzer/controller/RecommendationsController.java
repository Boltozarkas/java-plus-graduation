package ru.practicum.analyzer.controller;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.model.UserAction;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.analyzer.repository.UserActionRepository;
import ru.practicum.ewm.stats.proto.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RecommendationsController extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {
    private final EventSimilarityRepository similarityRepository;
    private final UserActionRepository userActionRepository;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request, StreamObserver<RecommendedEventProto> responseObserver) {
        Long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        // 1. Получаем недавно просмотренные мероприятия
        List<UserAction> userActions = userActionRepository.findByUserIdOrderByTimestampDesc(userId);
        if (userActions.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        List<Long> viewedEventIds = userActions.stream().map(UserAction::getEventId).toList();
        List<Long> limitedViewed = viewedEventIds.stream().limit(10).toList();

        // 2. Находим похожие мероприятия, с которыми пользователь еще не взаимодействовал
        Set<Long> candidates = new HashSet<>();
        for (Long viewedId : limitedViewed) {
            List<EventSimilarity> sims = similarityRepository.findByEventAOrEventBOrderByScoreDesc(viewedId, viewedId);
            for (EventSimilarity sim : sims) {
                Long otherId = sim.getEventA().equals(viewedId) ? sim.getEventB() : sim.getEventA();
                if (!viewedEventIds.contains(otherId)) {
                    candidates.add(otherId);
                }
            }
        }

        // 3. Для каждого кандидата вычисляем предсказанную оценку (взвешенное среднее)
        List<RecommendedEventProto> recommendations = new ArrayList<>();
        for (Long candidateId : candidates) {
            double predictedScore = calculatePredictedScore(userId, candidateId);
            if (predictedScore > 0) {
                recommendations.add(RecommendedEventProto.newBuilder()
                        .setEventId(candidateId)
                        .setScore(predictedScore)
                        .build());
            }
        }

        // 4. Сортируем и ограничиваем количество
        recommendations.sort(Comparator.comparingDouble(RecommendedEventProto::getScore).reversed());
        recommendations.stream().limit(maxResults).forEach(responseObserver::onNext);
        responseObserver.onCompleted();
    }

    private double calculatePredictedScore(Long userId, Long eventId) {
        // Получаем K похожих мероприятий, с которыми пользователь уже взаимодействовал
        List<EventSimilarity> sims = similarityRepository.findByEventAOrEventBOrderByScoreDesc(eventId, eventId);

        double weightedSum = 0.0;
        double similaritySum = 0.0;
        int k = 0;

        for (EventSimilarity sim : sims) {
            if (k >= 10) break; // Ограничиваем K
            Long otherId = sim.getEventA().equals(eventId) ? sim.getEventB() : sim.getEventA();

            // Ищем взаимодействие пользователя с этим мероприятием
            Optional<UserAction> action = userActionRepository.findByUserIdAndEventId(userId, otherId);
            if (action.isPresent()) {
                weightedSum += sim.getScore() * action.get().getWeight();
                similaritySum += sim.getScore();
                k++;
            }
        }

        return similaritySum > 0 ? weightedSum / similaritySum : 0.0;
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request, StreamObserver<RecommendedEventProto> responseObserver) {
        Long eventId = request.getEventId();
        Long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        List<EventSimilarity> sims = similarityRepository.findByEventAOrEventBOrderByScoreDesc(eventId, eventId);
        List<Long> otherEventIds = sims.stream()
                .map(s -> s.getEventA().equals(eventId) ? s.getEventB() : s.getEventA())
                .toList();

        List<UserAction> interactedActions = userActionRepository.findActionsByUserIdAndEventIdIn(userId, otherEventIds);
        Set<Long> interactedIds = interactedActions.stream().map(UserAction::getEventId).collect(Collectors.toSet());

        sims.stream()
                .filter(s -> {
                    Long other = s.getEventA().equals(eventId) ? s.getEventB() : s.getEventA();
                    return !interactedIds.contains(other);
                })
                .limit(maxResults)
                .map(s -> RecommendedEventProto.newBuilder()
                        .setEventId(s.getEventA().equals(eventId) ? s.getEventB() : s.getEventA())
                        .setScore(s.getScore())
                        .build())
                .forEach(responseObserver::onNext);

        responseObserver.onCompleted();
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request, StreamObserver<RecommendedEventProto> responseObserver) {
        List<Long> eventIds = request.getEventIdList();
        for (Long eventId : eventIds) {
            Double sum = userActionRepository.sumWeightByEventId(eventId);
            responseObserver.onNext(RecommendedEventProto.newBuilder()
                    .setEventId(eventId)
                    .setScore(sum != null ? sum : 0.0)
                    .build());
        }
        responseObserver.onCompleted();
    }
}