package ru.practicum.ewm.client;

import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.time.Instant;

@Slf4j
@Component
public class CollectorClient {

    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub client;

    public void sendView(Long userId, Long eventId) {
        sendAction(userId, eventId, ActionTypeProto.ACTION_VIEW);
    }

    public void sendRegister(Long userId, Long eventId) {
        sendAction(userId, eventId, ActionTypeProto.ACTION_REGISTER);
    }

    public void sendLike(Long userId, Long eventId) {
        sendAction(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }

    private void sendAction(Long userId, Long eventId, ActionTypeProto type) {
        try {
            Instant now = Instant.now();
            UserActionProto request = UserActionProto.newBuilder()
                    .setUserId(userId)
                    .setEventId(eventId)
                    .setActionType(type)
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .setNanos(now.getNano())
                            .build())
                    .build();
            client.collectUserAction(request);
            log.info("Sent action {} for user {} and event {}", type, userId, eventId);
        } catch (Exception e) {
            log.error("Failed to send action to Collector", e);
        }
    }
}