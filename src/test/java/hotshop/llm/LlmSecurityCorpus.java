package hotshop.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;

import hotshop.model.Condition;

/** Shared corpus loader; loading fixtures never creates clients or reads configuration. */
final class LlmSecurityCorpus {
    private LlmSecurityCorpus() {
    }

    static List<Case> load() throws IOException {
        InputStream source = LlmSecurityCorpus.class.getResourceAsStream("/hotshop/llm/security-corpus.json");
        if (source == null) {
            throw new IOException("Missing public LLM security corpus");
        }
        List<Case> cases = new ArrayList<>();
        try (JsonReader reader = new JsonReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                String id = null;
                String category = null;
                String title = null;
                Condition condition = null;
                boolean control = false;
                String review = null;
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "id" -> id = reader.nextString();
                        case "category" -> category = reader.nextString();
                        case "title" -> title = reader.nextString();
                        case "condition" -> condition = Condition.valueOf(reader.nextString());
                        case "control" -> control = reader.nextBoolean();
                        case "review" -> review = reader.nextString();
                        default -> throw new IOException("Unexpected public corpus field");
                    }
                }
                reader.endObject();
                cases.add(new Case(id, category, title, condition, control, review));
            }
            reader.endArray();
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
                throw new IOException("Unexpected trailing corpus data");
            }
        }
        return List.copyOf(cases);
    }

    record Case(String id, String category, String title, Condition condition, boolean control, String review) {
    }
}
