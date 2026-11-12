package cli;

import domain.Task;
import domain.TaskStatus;
import exception.ApplicationException;
import exception.CommandSyntaxException;
import exception.ErrorCode;
import exception.StorageException;
import service.TaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A konzolparancsok értelmezése és felhasználóbarát hibaüzenetei. */
public final class CommandParser {
    private final TaskManager taskManager;
    private final ConsoleRenderer renderer;

    public CommandParser(TaskManager taskManager, ConsoleRenderer renderer) {
        this.taskManager = Objects.requireNonNull(taskManager, "A TaskManager nem lehet null.");
        this.renderer = Objects.requireNonNull(renderer, "A megjelenítő nem lehet null.");
    }

    /** @return false kizárólag akkor, ha a program szabályosan kiléphet. */
    public boolean execute(String line) {
        try {
            List<String> tokens = tokenize(line);
            if (tokens.isEmpty()) {
                throw new CommandSyntaxException(ErrorCode.CMD_EMPTY, "Adj meg parancsot. Írd be: help.");
            }
            String command = tokens.get(0).toLowerCase(Locale.ROOT);
            return switch (command) {
                case "add" -> { add(tokens); yield true; }
                case "list" -> { list(tokens); yield true; }
                case "start" -> { start(tokens); yield true; }
                case "stop" -> { stop(tokens); yield true; }
                case "status" -> { status(tokens); yield true; }
                case "delete" -> { delete(tokens); yield true; }
                case "help" -> { requireCount(tokens, 1, "help"); printHelp(); yield true; }
                case "exit" -> exit(tokens);
                default -> throw new CommandSyntaxException(ErrorCode.CMD_UNKNOWN,
                        "Ismeretlen parancs: " + tokens.get(0) + ". Írd be: help.");
            };
        } catch (ApplicationException exception) {
            renderer.error(exception, exception instanceof StorageException && taskManager.hasUnsavedChanges());
            return true;
        }
    }

    public void printHelp() {
        renderer.printHelp();
    }

    private void add(List<String> tokens) throws ApplicationException {
        requireCount(tokens, 2, "add \"cím\"");
        Task task = taskManager.addTask(tokens.get(1));
        renderer.success("ÚJ FELADAT", "#%d — %s".formatted(task.getId(), task.getTitle()));
    }

    private void list(List<String> tokens) throws CommandSyntaxException {
        requireCount(tokens, 1, "list");
        renderer.renderTasks(taskManager.listTasks());
    }

    private void start(List<String> tokens) throws ApplicationException {
        requireCount(tokens, 2, "start <id>");
        Task task = taskManager.startTask(parseId(tokens.get(1)));
        renderer.success("IDŐMÉRÉS INDÍTVA", "#%d — %s".formatted(task.getId(), task.getTitle()));
    }

    private void stop(List<String> tokens) throws ApplicationException {
        requireCount(tokens, 2, "stop <id>");
        long id = parseId(tokens.get(1));
        long elapsed = taskManager.stopTask(id);
        renderer.success("IDŐMÉRÉS LEÁLLÍTVA", "#%d — %s került hozzáadásra.".formatted(id, formatDuration(elapsed)));
    }

    private void status(List<String> tokens) throws ApplicationException {
        requireCount(tokens, 3, "status <id> <TODO|IN_PROGRESS|DONE>");
        TaskStatus requested;
        try {
            requested = TaskStatus.valueOf(tokens.get(2).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CommandSyntaxException(ErrorCode.CMD_STATUS,
                    "Ismeretlen státusz. Használd ezeket: TODO, IN_PROGRESS, DONE.");
        }
        Task task = taskManager.changeStatus(parseId(tokens.get(1)), requested);
        renderer.info("STÁTUSZ FRISSÍTVE", "#%d új státusza: %s".formatted(task.getId(), task.getStatus()));
    }

    private void delete(List<String> tokens) throws ApplicationException {
        requireCount(tokens, 2, "delete <id>");
        long id = parseId(tokens.get(1));
        taskManager.deleteTask(id);
        renderer.success("FELADAT TÖRÖLVE", "#%d eltávolítva.".formatted(id));
    }

    private boolean exit(List<String> tokens) throws ApplicationException {
        requireCount(tokens, 1, "exit");
        taskManager.savePending();
        renderer.goodbye();
        return false;
    }

    private static long parseId(String token) throws CommandSyntaxException {
        try {
            long id = Long.parseLong(token);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new CommandSyntaxException(ErrorCode.CMD_ID, "Az ID pozitív egész szám legyen.");
        }
    }

    private static void requireCount(List<String> tokens, int count, String usage) throws CommandSyntaxException {
        if (tokens.size() != count) {
            throw new CommandSyntaxException(ErrorCode.CMD_ARGUMENTS, "Használat: " + usage);
        }
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
    }

    private static List<String> tokenize(String line) throws CommandSyntaxException {
        if (line == null || line.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        boolean tokenStarted = false;
        for (char character : line.toCharArray()) {
            if (escaped) {
                if (character != '"' && character != '\\') {
                    token.append('\\');
                }
                token.append(character);
                escaped = false;
                tokenStarted = true;
            } else if (character == '\\' && inQuotes) {
                escaped = true;
                tokenStarted = true;
            } else if (character == '"') {
                inQuotes = !inQuotes;
                tokenStarted = true;
            } else if (Character.isWhitespace(character) && !inQuotes) {
                if (tokenStarted) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    tokenStarted = false;
                }
            } else {
                token.append(character);
                tokenStarted = true;
            }
        }
        if (escaped || inQuotes) {
            throw new CommandSyntaxException(ErrorCode.CMD_QUOTING, "Lezáratlan idézőjel vagy escape-sorozat.");
        }
        if (tokenStarted) tokens.add(token.toString());
        return tokens;
    }
}
