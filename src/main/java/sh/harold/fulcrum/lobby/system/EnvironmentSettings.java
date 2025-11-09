package sh.harold.fulcrum.lobby.system;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility helpers for reading strongly typed values from the environment descriptor map.
 */
public final class EnvironmentSettings {
    private EnvironmentSettings() {
    }

    public static Optional<String> getString(Map<String, Object> settings, String... paths) {
        if (settings == null || settings.isEmpty() || paths == null) {
            return Optional.empty();
        }
        for (String path : paths) {
            Object value = resolve(settings, path);
            if (value == null) {
                continue;
            }
            String normalized = Objects.toString(value, "").trim();
            if (!normalized.isEmpty()) {
                return Optional.of(normalized);
            }
        }
        return Optional.empty();
    }

    public static List<String> getStringList(Map<String, Object> settings, String path) {
        Object value = resolve(settings, path);
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(element -> Objects.toString(element, "").trim())
                    .filter(element -> !element.isEmpty())
                    .toList();
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (!normalized.isEmpty()) {
                return List.of(normalized);
            }
        }
        return List.of();
    }

    public static Map<String, Object> getObjectMap(Map<String, Object> settings, String path) {
        Object value = resolve(settings, path);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        if (map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> {
            if (key != null && mapValue != null) {
                copy.put(key.toString(), mapValue);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object resolve(Map<String, Object> settings, String path) {
        if (settings == null || settings.isEmpty() || path == null || path.isBlank()) {
            return null;
        }
        Object current = settings;
        String[] segments = path.split("\\.");
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            if (!map.containsKey(segment)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
