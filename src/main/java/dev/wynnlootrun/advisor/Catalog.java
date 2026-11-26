package dev.wynnlootrun.advisor;

import static dev.wynnlootrun.advisor.State.Kind.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Catalog {
    record Entry(State.Kind kind, String name, int ev, String effect) {}
    static final List<Entry> ENTRIES = read();
    static final Map<String, Entry> BY_NAME = index();

    static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    static String clean(String value) {
        return value.replaceAll("§.", "").replace('’', '\'')
                .replaceAll("[\\p{Co}\\p{Cf}]", "").strip().replaceAll("\\s+", " ");
    }

    static Entry find(String name) {
        return BY_NAME.get(normalize(name));
    }

    private static Map<String, Entry> index() {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (result.put(normalize(entry.name()), entry) != null) {
                throw new IllegalStateException("Duplicate rule " + entry.name());
            }
        }
        return Map.copyOf(result);
    }

    private static List<Entry> read() {
        List<Entry> result = new ArrayList<>();
        try (var stream = Catalog.class.getResourceAsStream("/lootrun-rules.tsv");
             var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank()) continue;
                String[] part = line.split("\\t", 4);
                result.add(new Entry(State.Kind.valueOf(part[0]), part[1], Integer.parseInt(part[2]), part[3]));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read patch 2.2.6_4 rules", error);
        }
        if (result.stream().filter(e -> e.kind == MISSION).count() != 33
                || result.stream().filter(e -> e.kind == TRIAL).count() != 14
                || result.stream().filter(e -> e.kind == BOON).count() != 17) {
            throw new IllegalStateException("Incomplete patch 2.2.6_4 catalog");
        }
        return List.copyOf(result);
    }
}
