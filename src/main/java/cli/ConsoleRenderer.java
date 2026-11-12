package cli;

import domain.Task;
import domain.TaskStatus;
import exception.ApplicationException;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** ANSI-színeket és Unicode-kereteket használó, függőségmentes TUI-megjelenítő. */
public final class ConsoleRenderer {
    private static final int WIDTH = 78;
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String CYAN = "\u001B[36m";
    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String MAGENTA = "\u001B[35m";

    private final PrintWriter output;
    private final boolean colorsEnabled;

    public ConsoleRenderer(PrintWriter output) {
        this.output = Objects.requireNonNull(output, "A kimenet nem lehet null.");
        this.colorsEnabled = !Boolean.getBoolean("tasktracker.noColor");
    }

    public void welcome() {
        output.print("\u001B[2J\u001B[H");
        output.println(color("╔════════════════════════════════════════════════════════════════════════════╗", CYAN));
        output.println(color("║", CYAN) + color("                 SMART TASK TRACKER & TIME LOGGER", BOLD + MAGENTA)
                + color("                 ║", CYAN));
        output.println(color("║", CYAN) + color("                 Fókuszált munka. Átlátható idő.                  ", DIM)
                + color("                 ║", CYAN));
        output.println(color("╚════════════════════════════════════════════════════════════════════════════╝", CYAN));
        output.println(color("  Írd be: ", DIM) + color("help", BOLD + YELLOW)
                + color("  a parancsok megjelenítéséhez.", DIM));
        output.flush();
    }

    public void prompt() {
        output.print(color(" task-tracker", BOLD + CYAN) + color(" › ", BOLD + MAGENTA));
        output.flush();
    }

    public void historyHint() {
        output.println(color("  Tipp: a ↑ és ↓ nyíllal az előző parancsok hívhatók vissza.", DIM));
        output.flush();
    }

    public void success(String title, String message) {
        box(title, List.of(message), GREEN);
    }

    public void info(String title, String message) {
        box(title, List.of(message), BLUE);
    }

    public void error(ApplicationException exception, boolean unsavedChanges) {
        String detail = "[" + exception.getCode() + "] " + exception.getMessage();
        if (unsavedChanges) {
            box("MENTÉSI FIGYELMEZTETÉS", List.of(detail,
                    "A módosítás memóriában megvan, de még nincs biztonságosan elmentve."), RED);
        } else {
            box("HIBA", List.of(detail), RED);
        }
    }

    public void renderTasks(List<Task> tasks) {
        if (tasks.isEmpty()) {
            info("FELADATOK", "Még nincs rögzített feladat. Kezdd ezzel: add \"Első feladat\"");
            return;
        }
        border("┌", "─", "┐", "FELADATOK", CYAN);
        String headings = String.format(" %-6s %-14s %-35s %12s ", "ID", "STÁTUSZ", "CÍM", "IDŐ");
        output.println(color("│", CYAN) + color(pad(headings), BOLD + DIM) + color("│", CYAN));
        output.println(color("├" + "─".repeat(WIDTH - 2) + "┤", CYAN));
        for (Task task : tasks) {
            String status = pad(task.getStatus().name(), 14);
            String row = String.format(" #%-5d %s %-35s %12s ", task.getId(), status,
                    truncate(task.getTitle(), 35), formatDuration(displayedSeconds(task)));
            int statusStart = 8;
            String beforeStatus = row.substring(0, statusStart);
            String afterStatus = row.substring(statusStart + status.length());
            output.println(color("│", CYAN) + beforeStatus + statusColor(status, task.getStatus()) + afterStatus
                    + " ".repeat(Math.max(0, WIDTH - 2 - row.length())) + color("│", CYAN));
        }
        output.println(color("└" + "─".repeat(WIDTH - 2) + "┘", CYAN));
        output.flush();
    }

    public void printHelp() {
        border("┌", "─", "┐", "PARANCSOK", YELLOW);
        command("add \"cím\"", "Új feladat felvétele");
        command("list", "Feladatok listázása");
        command("start <id>", "Időmérés indítása");
        command("stop <id>", "Időmérés leállítása");
        command("status <id> <állapot>", "TODO, IN_PROGRESS vagy DONE");
        command("delete <id>", "Nem futó feladat törlése");
        command("help", "Ez a súgó");
        command("exit", "Mentés és kilépés");
        output.println(color("└" + "─".repeat(WIDTH - 2) + "┘", YELLOW));
        output.flush();
    }

    public void goodbye() {
        success("MENTÉS KÉSZ", "Az adatok biztonságosan mentve. Viszontlátásra!");
    }

    public void fatal(String message) {
        box("INDÍTÁSI HIBA", List.of(message, "A mentési fájl védelmében az alkalmazás leáll."), RED);
    }

    public void unexpectedError() {
        box("VÁRATLAN HIBA", List.of("Belső hiba történt. A részletek fejlesztői naplóban vizsgálhatók."), RED);
    }

    private void command(String syntax, String description) {
        String row = String.format("  %-31s %s", syntax, description);
        output.println(color("│", YELLOW) + color(pad(row), DIM) + color("│", YELLOW));
    }

    private void box(String title, List<String> lines, String accent) {
        border("┌", "─", "┐", title, accent);
        for (String line : lines) {
            output.println(color("│", accent) + pad("  " + line) + color("│", accent));
        }
        output.println(color("└" + "─".repeat(WIDTH - 2) + "┘", accent));
        output.flush();
    }

    private void border(String left, String horizontal, String right, String title, String accent) {
        String label = " " + title + " ";
        int remaining = WIDTH - 2 - label.length();
        output.println(color(left + label + horizontal.repeat(Math.max(0, remaining)) + right, accent));
    }

    private String statusColor(String text, TaskStatus status) {
        return switch (status) {
            case TODO -> color(text, YELLOW);
            case IN_PROGRESS -> color(text, MAGENTA + BOLD);
            case DONE -> color(text, GREEN);
        };
    }

    private String color(String text, String ansiCode) {
        return colorsEnabled ? ansiCode + text + RESET : text;
    }

    private static long displayedSeconds(Task task) {
        if (task.getStartedAt().isEmpty()) {
            return task.getAccumulatedSeconds();
        }
        return task.getAccumulatedSeconds()
                + Math.max(0, Duration.between(task.getStartedAt().get(), Instant.now()).getSeconds());
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
    }

    private static String truncate(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length - 1) + "…";
    }

    private static String pad(String text) {
        return text + " ".repeat(Math.max(0, WIDTH - 2 - text.length()));
    }

    private static String pad(String text, int length) {
        return text + " ".repeat(Math.max(0, length - text.length()));
    }
}
