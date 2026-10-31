package ru.practicum.collector.controller;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.collector.service.UserActionProducer;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.time.Instant;

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
            default -> throw new IllegalArgumentException("Unknown action type: " + request.getActionType());
        }

        UserActionAvro avro = UserActionAvro.newBuilder()
                .setUserId(request.getUserId())
                .setEventId(request.getEventId())
                .setActionType(avroType)
                .setTimestamp(Instant.ofEpochSecond(
                        request.getTimestamp().getSeconds(),
                        request.getTimestamp().getNanos()))
                .build();

        producer.sendUserAction(avro);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}