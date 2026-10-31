package ru.practicum.analyzer.serializer;

import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.serializer.BaseAvroDeserializer;

public class EventSimilarityDeserializer extends BaseAvroDeserializer<EventSimilarityAvro> {
    public EventSimilarityDeserializer() {
        super(EventSimilarityAvro.getClassSchema());
    }
}