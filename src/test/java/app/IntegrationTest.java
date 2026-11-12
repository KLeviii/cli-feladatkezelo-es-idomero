package app;

import cli.CommandParser;
import cli.ConsoleRenderer;
import domain.Task;
import exception.ErrorCode;
import exception.TaskStateException;
import persistence.StorageHandler;
import service.TaskManager;
import service.TimeTracker;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Egyszerű, függőségmentes integrációs ellenőrzés. */
public final class IntegrationTest {
    private IntegrationTest() { }

    public static void main(String[] args) throws Exception {
        Path storageFile = Path.of("out", "task-tracker-test", "tasks.json");
        Files.deleteIfExists(storageFile);
        Instant startedAt = Instant.parse("2026-10-29T10:00:00Z");
        TaskManager firstRun = manager(storageFile, startedAt);

        firstRun.load();
        Task design = firstRun.addTask("Tervezés");
        firstRun.startTask(design.getId());

        TaskManager secondRun = manager(storageFile, startedAt.plusSeconds(90));
        secondRun.load();
        long elapsed = secondRun.stopTask(design.getId());
        assertEquals(90L, elapsed, "A mért idő hibás.");
        assertEquals(90L, secondRun.listTasks().get(0).getAccumulatedSeconds(),
                "A mentett idő nem maradt meg.");

        Task implementation = secondRun.addTask("Implementálás");
        secondRun.startTask(design.getId());
        try {
            secondRun.startTask(implementation.getId());
            throw new AssertionError("A második aktív feladat indítása nem okozott hibát.");
        } catch (TaskStateException exception) {
            assertEquals(ErrorCode.TASK_ALREADY_RUNNING, exception.getCode(), "Rossz hibakód.");
        }

        Path cliStorageFile = Path.of("out", "task-tracker-cli-test", "tasks.json");
        Files.deleteIfExists(cliStorageFile);
        StringWriter cliOutput = new StringWriter();
        TaskManager cliManager = manager(cliStorageFile, startedAt);
        cliManager.load();
        CommandParser parser = new CommandParser(cliManager,
                new ConsoleRenderer(new PrintWriter(cliOutput, true)));
        parser.execute("add \"CLI feladat\"");
        parser.execute("start 1");
        parser.execute("stop 1");
        parser.execute("start nem-szám");
        assertTrue(cliOutput.toString().contains("[CMD_ID]"),
                "A CLI nem jelenítette meg a várt hibakódot.");

        System.out.println("IntegrationTest: sikeres");
    }

    private static TaskManager manager(Path storageFile, Instant instant) {
        return new TaskManager(new StorageHandler(storageFile),
                new TimeTracker(Clock.fixed(instant, ZoneOffset.UTC)));
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " Várt: " + expected + ", kapott: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
