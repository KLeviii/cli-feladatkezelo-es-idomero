package persistence;

import domain.Task;
import domain.TaskStatus;
import exception.ErrorCode;
import exception.StorageCorruptedException;
import exception.StorageException;
import exception.StorageReadException;
import exception.StorageWriteException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** UTF-8-as JSON-fájl kezelése külső függőség nélkül. */
public final class StorageHandler implements TaskRepository {
    private static final long SCHEMA_VERSION = 1;
    private final Path storagePath;

    public StorageHandler(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "A tárolási útvonal nem lehet null.");
    }

    @Override
    public TaskData load() throws StorageException {
        if (Files.notExists(storagePath)) {
            return TaskData.empty();
        }
        try {
            Object root = new JsonReader(Files.readString(storagePath, StandardCharsets.UTF_8)).parse();
            return readTaskData(asObject(root, "A JSON gyökéreleme objektum legyen."));
        } catch (StorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new StorageReadException(ErrorCode.STORAGE_READ,
                    "A mentési fájl nem olvasható: " + storagePath, exception);
        } catch (IllegalArgumentException exception) {
            throw new StorageCorruptedException(ErrorCode.STORAGE_CORRUPTED,
                    "A mentési fájl hibás JSON-t tartalmaz: " + storagePath, exception);
        }
    }

    @Override
    public void save(TaskData taskData) throws StorageException {
        Path absolutePath = storagePath.toAbsolutePath();
        Path parent = absolutePath.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "tasks-", ".json.tmp");
            Files.writeString(temporary, writeTaskData(taskData), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, absolutePath, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new StorageWriteException(ErrorCode.STORAGE_WRITE,
                    "A mentési fájl nem írható: " + storagePath, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A következő mentésnél nem befolyásolja az alkalmazás állapotát.
                }
            }
        }
    }

    private TaskData readTaskData(Map<String, Object> root) throws StorageException {
        long schemaVersion = requiredLong(root, "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new StorageCorruptedException(ErrorCode.STORAGE_SCHEMA,
                    "Nem támogatott mentési séma: " + schemaVersion + ".");
        }
        long nextId = requiredLong(root, "nextId");
        if (nextId <= 0) {
            throw invalidState("A nextId pozitív egész legyen.");
        }
        Object taskValue = root.get("tasks");
        if (!(taskValue instanceof List<?> rawTasks)) {
            throw schemaError("A tasks mező tömb legyen.");
        }
        List<Task> tasks = new ArrayList<>();
        for (Object rawTask : rawTasks) {
            tasks.add(readTask(asObject(rawTask, "A feladat objektum legyen.")));
        }
        return new TaskData(nextId, tasks);
    }

    private Task readTask(Map<String, Object> rawTask) throws StorageException {
        try {
            long id = requiredLong(rawTask, "id");
            String title = requiredString(rawTask, "title");
            String statusName = requiredString(rawTask, "status");
            TaskStatus status = TaskStatus.valueOf(statusName);
            long accumulatedSeconds = requiredLong(rawTask, "accumulatedSeconds");
            Instant startedAt = optionalInstant(rawTask.get("startedAt"));
            return new Task(id, title, status, accumulatedSeconds, startedAt);
        } catch (IllegalArgumentException exception) {
            throw invalidState("Érvénytelen feladat a mentési fájlban: " + exception.getMessage(), exception);
        }
    }

    private static Instant optionalInstant(Object value) throws StorageException {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw schemaError("A startedAt mező szöveg vagy null legyen.");
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException exception) {
            throw invalidState("A startedAt mező nem ISO-8601 időpont.", exception);
        }
    }

    private static Map<String, Object> asObject(Object value, String message) throws StorageException {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw schemaError(message);
                }
                result.put(key, entry.getValue());
            }
            return result;
        }
        throw schemaError(message);
    }

    private static long requiredLong(Map<String, Object> object, String key) throws StorageException {
        Object value = object.get(key);
        if (value instanceof Long number) {
            return number;
        }
        throw schemaError("A " + key + " mező egész szám legyen.");
    }

    private static String requiredString(Map<String, Object> object, String key) throws StorageException {
        Object value = object.get(key);
        if (value instanceof String text) {
            return text;
        }
        throw schemaError("A " + key + " mező szöveg legyen.");
    }

    private static StorageCorruptedException schemaError(String message) {
        return new StorageCorruptedException(ErrorCode.STORAGE_SCHEMA, message);
    }

    private static StorageCorruptedException invalidState(String message) {
        return new StorageCorruptedException(ErrorCode.STORAGE_INVALID_STATE, message);
    }

    private static StorageCorruptedException invalidState(String message, Throwable cause) {
        return new StorageCorruptedException(ErrorCode.STORAGE_INVALID_STATE, message, cause);
    }

    private static String writeTaskData(TaskData taskData) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"schemaVersion\": ").append(SCHEMA_VERSION)
                .append(",\n  \"nextId\": ").append(taskData.nextId())
                .append(",\n  \"tasks\": [");
        for (int index = 0; index < taskData.tasks().size(); index++) {
            Task task = taskData.tasks().get(index);
            json.append(index == 0 ? "\n" : ",\n");
            json.append("    {\n")
                    .append("      \"id\": ").append(task.getId()).append(",\n")
                    .append("      \"title\": \"").append(escape(task.getTitle())).append("\",\n")
                    .append("      \"status\": \"").append(task.getStatus()).append("\",\n")
                    .append("      \"accumulatedSeconds\": ").append(task.getAccumulatedSeconds()).append(",\n")
                    .append("      \"startedAt\": ");
            if (task.getStartedAt().isPresent()) {
                json.append("\"").append(task.getStartedAt().get()).append("\"");
            } else {
                json.append("null");
            }
            json.append("\n    }");
        }
        json.append(taskData.tasks().isEmpty() ? "]\n}" : "\n  ]\n}");
        return json.toString();
    }

    private static String escape(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char character : text.toCharArray()) {
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /** A tárolt, szűk JSON-nyelv olvasója. */
    private static final class JsonReader {
        private final String source;
        private int position;

        private JsonReader(String source) { this.source = source; }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (position != source.length()) {
                throw error("Váratlan karakter a JSON végén.");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= source.length()) throw error("Hiányzó JSON-érték.");
            return switch (source.charAt(position)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 'n' -> { expect("null"); yield null; }
                case 't' -> { expect("true"); yield Boolean.TRUE; }
                case 'f' -> { expect("false"); yield Boolean.FALSE; }
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return object;
            do {
                skipWhitespace();
                if (position >= source.length() || source.charAt(position) != '"') throw error("Objektumkulcs hiányzik.");
                String key = parseString();
                skipWhitespace();
                expect(':');
                if (object.put(key, parseValue()) != null) throw error("Ismétlődő JSON-kulcs: " + key);
                skipWhitespace();
            } while (consume(','));
            expect('}');
            return object;
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return values;
            do {
                values.add(parseValue());
                skipWhitespace();
            } while (consume(','));
            expect(']');
            return values;
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < source.length()) {
                char character = source.charAt(position++);
                if (character == '"') return result.toString();
                if (character == '\\') {
                    if (position >= source.length()) throw error("Befejezetlen escape-sorozat.");
                    char escaped = source.charAt(position++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(parseUnicode());
                        default -> throw error("Érvénytelen escape-sorozat.");
                    }
                } else {
                    if (character < 0x20) throw error("Vezérlőkarakter a JSON-szövegben.");
                    result.append(character);
                }
            }
            throw error("Lezáratlan JSON-szöveg.");
        }

        private char parseUnicode() {
            if (position + 4 > source.length()) throw error("Hiányos Unicode escape.");
            String hex = source.substring(position, position + 4);
            position += 4;
            try { return (char) Integer.parseInt(hex, 16); }
            catch (NumberFormatException exception) { throw error("Érvénytelen Unicode escape."); }
        }

        private Long parseNumber() {
            int start = position;
            if (consume('-')) { /* optional sign */ }
            if (position >= source.length() || !Character.isDigit(source.charAt(position))) throw error("Érvénytelen szám.");
            while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
            if (position < source.length() && (source.charAt(position) == '.' || source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                throw error("A mentési formátum csak egész számokat támogat.");
            }
            try { return Long.parseLong(source.substring(start, position)); }
            catch (NumberFormatException exception) { throw error("A szám kívül esik a long tartományán."); }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (position >= source.length() || source.charAt(position) != expected) throw error("Hiányzó karakter: " + expected);
            position++;
        }

        private void expect(String expected) {
            if (!source.startsWith(expected, position)) throw error("Érvénytelen JSON-literál.");
            position += expected.length();
        }

        private boolean consume(char expected) {
            if (position < source.length() && source.charAt(position) == expected) { position++; return true; }
            return false;
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " Pozíció: " + position + ".");
        }
    }
}
