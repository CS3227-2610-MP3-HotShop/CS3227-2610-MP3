package hotshop.llm;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** Strict, bounded provider envelope validation; this does not judge description semantics. */
final class LlmResponseParser {
    private static final int MAX_BODY_BYTES = 65536;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_DESCRIPTION_CODE_POINTS = 5000;
    private static final int MAX_WORDS = 100;

    private LlmResponseParser() {
    }

    static LlmClient.Result parse(byte[] body) {
        if (body == null || body.length > MAX_BODY_BYTES) {
            return invalid();
        }
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body)).toString();
            try (JsonReader reader = new JsonReader(new StringReader(json))) {
                reader.setStrictness(Strictness.STRICT);
                String description = readEnvelope(reader);
                require(reader.peek() == JsonToken.END_DOCUMENT);
                description = description.strip();
                require(description.codePointCount(0, description.length()) <= MAX_DESCRIPTION_CODE_POINTS);
                int words = countWords(description);
                require(words > 0 && words <= MAX_WORDS);
                return new LlmClient.Generated(description);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            // Provider bytes and parser diagnostics are never attached to public failures.
            return invalid();
        }
    }

    private static String readEnvelope(JsonReader reader) throws IOException {
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String status = null;
        String description = null;
        while (reader.hasNext()) {
            String name = nextName(reader, names);
            switch (name) {
                case "status" -> status = readString(reader);
                case "output_text" -> description = readString(reader);
                case "output" -> readOutput(reader, 2);
                default -> skipValue(reader, 2);
            }
        }
        reader.endObject();
        require("completed".equals(status) && description != null);
        return description;
    }

    private static void readOutput(JsonReader reader, int depth) throws IOException {
        require(depth <= MAX_DEPTH);
        reader.beginArray();
        while (reader.hasNext()) {
            readMessage(reader, depth + 1);
        }
        reader.endArray();
    }

    private static void readMessage(JsonReader reader, int depth) throws IOException {
        require(depth <= MAX_DEPTH);
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String type = null;
        String role = null;
        String status = null;
        boolean content = false;
        while (reader.hasNext()) {
            String name = nextName(reader, names);
            switch (name) {
                case "type" -> type = readString(reader);
                case "role" -> role = readString(reader);
                case "status" -> status = readString(reader);
                case "content" -> {
                    readContent(reader, depth + 1);
                    content = true;
                }
                default -> skipValue(reader, depth + 1);
            }
        }
        reader.endObject();
        require("message".equals(type) && "assistant".equals(role) && "completed".equals(status) && content);
    }

    private static void readContent(JsonReader reader, int depth) throws IOException {
        require(depth <= MAX_DEPTH);
        reader.beginArray();
        while (reader.hasNext()) {
            readText(reader, depth + 1);
        }
        reader.endArray();
    }

    private static void readText(JsonReader reader, int depth) throws IOException {
        require(depth <= MAX_DEPTH);
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String type = null;
        boolean text = false;
        while (reader.hasNext()) {
            String name = nextName(reader, names);
            switch (name) {
                case "type" -> type = readString(reader);
                case "text" -> {
                    readString(reader);
                    text = true;
                }
                default -> skipValue(reader, depth + 1);
            }
        }
        reader.endObject();
        require("output_text".equals(type) && text);
    }

    private static void skipValue(JsonReader reader, int depth) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                require(depth <= MAX_DEPTH);
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    nextName(reader, names);
                    skipValue(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                require(depth <= MAX_DEPTH);
                reader.beginArray();
                while (reader.hasNext()) {
                    skipValue(reader, depth + 1);
                }
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> require(false);
        }
    }

    private static String nextName(JsonReader reader, Set<String> names) throws IOException {
        String name = reader.nextName();
        require(names.add(name));
        return name;
    }

    private static String readString(JsonReader reader) throws IOException {
        require(reader.peek() == JsonToken.STRING);
        return reader.nextString();
    }

    private static int countWords(String text) {
        int words = 0;
        boolean letterOrDigit = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (letterOrDigit) {
                    words++;
                }
                letterOrDigit = false;
            } else if (Character.isLetterOrDigit(codePoint)) {
                letterOrDigit = true;
            }
        }
        return words + (letterOrDigit ? 1 : 0);
    }

    private static void require(boolean valid) throws IOException {
        if (!valid) {
            throw new IOException("Invalid provider response");
        }
    }

    private static LlmClient.Failed invalid() {
        return new LlmClient.Failed(LlmClient.Failure.INVALID_RESPONSE);
    }
}
