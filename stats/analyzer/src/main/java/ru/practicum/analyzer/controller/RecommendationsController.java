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
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        Long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        // 1. Получаем всю историю взаимодействий пользователя
        List<UserAction> userActions = userActionRepository.findByUserIdOrderByTimestampDesc(userId);
        if (userActions.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        // 2. Загружаем ВСЕ взаимодействия пользователя один раз
        Map<Long, Double> userEventWeights = userActions.stream()
                .collect(Collectors.toMap(UserAction::getEventId, UserAction::getWeight, (w1, w2) -> w1));

        // 3. Множество eventId, с которыми пользователь взаимодействовал — для O(1) проверки
        Set<Long> viewedEventIds = userActions.stream()
                .map(UserAction::getEventId)
                .collect(Collectors.toSet());

        // 4. ОДИН запрос: получаем все сходства для ВСЕЙ истории (без произвольного limit)
        List<EventSimilarity> viewedSims = similarityRepository.findAllByEventIds(viewedEventIds);

        // 5. Собираем кандидатов: события, похожие на просмотренные, с которыми пользователь НЕ взаимодействовал
        Set<Long> candidates = new HashSet<>();
        for (EventSimilarity sim : viewedSims) {
            Long a = sim.getEventA();
            Long b = sim.getEventB();
            if (viewedEventIds.contains(a) && !viewedEventIds.contains(b)) {
                candidates.add(b);
            } else if (viewedEventIds.contains(b) && !viewedEventIds.contains(a)) {
                candidates.add(a);
            }
        }

        if (candidates.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        // 6. ОДИН запрос: получаем все сходства для всех кандидатов (для расчёта score)
        List<EventSimilarity> candidateSims = similarityRepository.findAllByEventIds(candidates);

        // 7. Группируем сходства кандидатов в Map: candidateId -> List<EventSimilarity>
        Map<Long, List<EventSimilarity>> simsByEvent = new HashMap<>();
        for (EventSimilarity sim : candidateSims) {
            simsByEvent.computeIfAbsent(sim.getEventA(), k -> new ArrayList<>()).add(sim);
            simsByEvent.computeIfAbsent(sim.getEventB(), k -> new ArrayList<>()).add(sim);
        }

        // 8. Считаем оценки в памяти (внутри calculatePredictedScore — K=10 ближайших соседей, это отдельное требование)
        List<RecommendedEventProto> recommendations = new ArrayList<>();
        for (Long candidateId : candidates) {
            List<EventSimilarity> sims = simsByEvent.getOrDefault(candidateId, Collections.emptyList());
            double predictedScore = calculatePredictedScore(candidateId, sims, userEventWeights);
            if (predictedScore > 0) {
                recommendations.add(RecommendedEventProto.newBuilder()
                        .setEventId(candidateId)
                        .setScore(predictedScore)
                        .build());
            }
        }

        // 9. Сортируем и ограничиваем количество
        recommendations.sort(Comparator.comparingDouble(RecommendedEventProto::getScore).reversed());
        recommendations.stream().limit(maxResults).forEach(responseObserver::onNext);
        responseObserver.onCompleted();
    }

    private double calculatePredictedScore(Long eventId,
                                           List<EventSimilarity> sims,
                                           Map<Long, Double> userEventWeights) {
        double weightedSum = 0.0;
        double similaritySum = 0.0;
        int k = 0;

        // Сходства уже отсортированы по score desc (в запросе), но на всякий случай
        // можно не полагаться на это и ограничиться первыми 10
        for (EventSimilarity sim : sims) {
            if (k >= 10) break;

            Long otherId = sim.getEventA().equals(eventId) ? sim.getEventB() : sim.getEventA();
            Double weight = userEventWeights.get(otherId);

            if (weight != null) {
                weightedSum += sim.getScore() * weight;
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

        // Один запрос к сходствам
        List<EventSimilarity> sims = similarityRepository.findByEventAOrEventBOrderByScoreDesc(eventId, eventId);
        List<Long> otherEventIds = sims.stream()
                .map(s -> s.getEventA().equals(eventId) ? s.getEventB() : s.getEventA())
                .toList();

        // Один запрос к взаимодействиям пользователя (уже по списку eventIds)
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
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        List<Long> eventIds = request.getEventIdList();

        if (eventIds.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        // ОДИН запрос: суммы весов по всем запрошенным событиям
        Map<Long, Double> sumsByEventId = new HashMap<>();
        for (Object[] row : userActionRepository.sumWeightsByEventIds(eventIds)) {
            Long eventId = ((Number) row[0]).longValue();
            Double sum = ((Number) row[1]).doubleValue();
            sumsByEventId.put(eventId, sum);
        }

        // Формируем ответ; для событий без взаимодействий — 0.0
        for (Long eventId : eventIds) {
            double score = sumsByEventId.getOrDefault(eventId, 0.0);
            responseObserver.onNext(RecommendedEventProto.newBuilder()
                    .setEventId(eventId)
                    .setScore(score)
                    .build());
        }
        responseObserver.onCompleted();
    }
}