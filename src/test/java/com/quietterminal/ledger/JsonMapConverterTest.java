package com.quietterminal.ledger;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.persistence.JsonMapConverter;

import static org.junit.jupiter.api.Assertions.*;

class JsonMapConverterTest {

    private final JsonMapConverter converter = new JsonMapConverter();

    @Test
    void convertToDatabaseColumnReturnsNullForNullAttribute() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToDatabaseColumnSerializesAMapToJson() {
        String json = converter.convertToDatabaseColumn(Map.of("key", "value"));

        assertEquals("{\"key\":\"value\"}", json);
    }

    @Test
    void convertToDatabaseColumnSerializesAnEmptyMapToAnEmptyJsonObject() {
        String json = converter.convertToDatabaseColumn(Map.of());

        assertEquals("{}", json);
    }

    @Test
    void convertToEntityAttributeReturnsAnEmptyMapForNullDbData() {
        Map<String, Object> result = converter.convertToEntityAttribute(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void convertToEntityAttributeDeserializesJsonToAMap() {
        Map<String, Object> result = converter.convertToEntityAttribute("{\"key\":\"value\"}");

        assertEquals(Map.of("key", "value"), result);
    }

    @Test
    void roundTripPreservesData() {
        Map<String, Object> original = Map.of("key", "value", "count", 3);

        String json = converter.convertToDatabaseColumn(original);
        Map<String, Object> result = converter.convertToEntityAttribute(json);

        assertEquals(original, result);
    }
}
