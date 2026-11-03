package ru.practicum.ewm.client;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AnalyzerClient {

    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub client;

    public double getRating(Long eventId) {
        InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                .addEventId(eventId)
                .build();
        var iterator = client.getInteractionsCount(request);
        if (iterator.hasNext()) {
            return iterator.next().getScore();
        }
        return 0.0;
    }

    public Map<Long, Double> getRatings(List<Long> eventIds) {
        InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                .addAllEventId(eventIds)
                .build();
        var iterator = client.getInteractionsCount(request);
        Map<Long, Double> result = new HashMap<>();
        while (iterator.hasNext()) {
            RecommendedEventProto proto = iterator.next();
            result.put(proto.getEventId(), proto.getScore());
        }
        return result;
    }

    public Map<Long, Double> getRecommendationsForUser(Long userId, int maxResults) {
        UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                .setUserId(userId)
                .setMaxResults(maxResults)
                .build();
        var iterator = client.getRecommendationsForUser(request);
        Map<Long, Double> result = new HashMap<>();
        while (iterator.hasNext()) {
            RecommendedEventProto proto = iterator.next();
            result.put(proto.getEventId(), proto.getScore());
        }
        return result;
    }
}