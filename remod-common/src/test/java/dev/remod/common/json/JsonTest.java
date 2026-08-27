package dev.remod.common.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesTheShapeOfAModManifest() {
        JsonObject manifest = Json.parseObject("{\n"
                + "  \"id\": \"simplemod\",\n"
                + "  \"name\": \"ReMod Simple Mod\",\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"minecraft\": \"1.21.x\",\n"
                + "  \"remod_api\": \"1.21-1.0.0\",\n"
                + "  \"client_only\": false,\n"
                + "  \"entrypoints\": [\"dev.example.Main\"],\n"
                + "  \"dependencies\": []\n"
                + "}");

        assertEquals("simplemod", manifest.getString("id"));
        assertEquals("1.21-1.0.0", manifest.getString("remod_api"));
        assertFalse(manifest.getBoolean("client_only"));
        assertEquals(java.util.List.of("dev.example.Main"), manifest.optStringList("entrypoints"));
        assertTrue(manifest.optArray("dependencies").isEmpty());
    }

    @Test
    void parsesNestedStructuresAndNumberKinds() {
        JsonObject root = Json.parseObject(
                "{\"a\":{\"b\":[1, 2.5, -3, true, null, \"x\"]},\"big\":9007199254740993}");
        JsonArray array = root.getObject("a").getArray("b");
        assertEquals(6, array.size());
        assertEquals(1L, array.get(0));
        assertEquals(2.5d, array.get(1));
        assertEquals(-3L, array.get(2));
        assertEquals(Boolean.TRUE, array.get(3));
        assertNull(array.get(4));
        assertEquals("x", array.getString(5));
        // Long is preferred over double so large ids survive a round trip exactly.
        assertEquals(9007199254740993L, root.getLong("big"));
    }

    @Test
    void handlesEscapesAndUnicode() {
        JsonObject root = Json.parseObject("{\"s\":\"line\\nbreak \\u00e9 \\\"quoted\\\" \\\\slash\"}");
        assertEquals("line\nbreak é \"quoted\" \\slash", root.getString("s"));
    }

    @Test
    void roundTripsThroughTheWriter() {
        String original = "{\"a\":[1,{\"b\":\"c\\n\"}],\"d\":true,\"e\":null}";
        Object parsed = Json.parse(original);
        assertEquals(original, Json.write(parsed));
        // Pretty output must reparse to exactly the same document.
        assertEquals(parsed, Json.parse(Json.writePretty(parsed)));
    }

    @Test
    void rejectsMalformedDocumentsWithPositionInformation() {
        JsonException error = assertThrows(JsonException.class,
                () -> Json.parseObject("{\"a\": 1,}"));
        assertTrue(error.getMessage().contains("Trailing comma"), error.getMessage());
        assertTrue(error.getMessage().contains("line 1"), error.getMessage());

        assertThrows(JsonException.class, () -> Json.parseObject("{a: 1}"));
        assertThrows(JsonException.class, () -> Json.parseObject("{\"a\": 1} garbage"));
        assertThrows(JsonException.class, () -> Json.parseObject("[1,2]"));
        assertThrows(JsonException.class, () -> Json.parseObject("{\"a\": \"unterminated"));
    }

    @Test
    void rejectsPathologicallyDeepDocuments() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            deep.append("[");
        }
        assertThrows(JsonException.class, () -> Json.parse(deep.toString()));
    }

    @Test
    void typedAccessorsNameTheOffendingKey() {
        JsonObject object = Json.parseObject("{\"version\": 3}");
        JsonException missing = assertThrows(JsonException.class, () -> object.getString("id"));
        assertTrue(missing.getMessage().contains("'id'"), missing.getMessage());

        JsonException wrongType = assertThrows(JsonException.class, () -> object.getString("version"));
        assertTrue(wrongType.getMessage().contains("'version'"), wrongType.getMessage());
        assertTrue(wrongType.getMessage().contains("number"), wrongType.getMessage());
    }

    @Test
    void optionalAccessorsNeverReturnNullContainers() {
        JsonObject empty = new JsonObject();
        assertTrue(empty.optObject("missing").isEmpty());
        assertTrue(empty.optArray("missing").isEmpty());
        assertTrue(empty.optStringList("missing").isEmpty());
        assertEquals("fallback", empty.optString("missing", "fallback"));
    }

    @Test
    void skipsAByteOrderMark() {
        assertEquals("v", Json.parseObject("﻿{\"k\":\"v\"}").getString("k"));
    }
}
