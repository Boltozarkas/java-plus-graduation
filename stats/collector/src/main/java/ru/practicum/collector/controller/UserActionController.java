package ru.practicum.collector.controller;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.collector.service.UserActionProducer;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.time.Instant;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserActionController extends UserActionControllerGrpc.UserActionControllerImplBase {
    private final UserActionProducer producer;

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        ActionTypeAvro avroType;
        switch (request.getActionType()) {
            case ACTION_VIEW -> avroType = ActionTypeAvro.VIEW;
            case ACTION_REGISTER -> avroType = ActionTypeAvro.REGISTER;
            case ACTION_LIKE -> avroType = ActionTypeAvro.LIKE;
            default -> {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Unknown action type: " + request.getActionType())
                        .asRuntimeException());
                return;
            }
        }

        UserActionAvro avro = UserActionAvro.newBuilder()
                .setUserId(request.getUserId())
                .setEventId(request.getEventId())
                .setActionType(avroType)
                .setTimestamp(Instant.ofEpochSecond(
                        request.getTimestamp().getSeconds(),
                        request.getTimestamp().getNanos()))
                .build();

        try {
            // Блокирующая отправка: ждём подтверждения от Kafka
            producer.sendUserAction(avro);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to collect user action", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to send user action to Kafka: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}