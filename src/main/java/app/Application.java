package app;

import cli.CommandParser;
import cli.ConsoleRenderer;
import exception.StorageException;
import persistence.StorageHandler;
import service.TaskManager;
import service.TimeTracker;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

/** Az alkalmazás belépési pontja és interaktív konzolciklusa. */
public final class Application {
    private Application() { }

    public static void main(String[] args) {
        Path storagePath = Path.of("data", "tasks.json");
        PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        ConsoleRenderer renderer = new ConsoleRenderer(output);
        TaskManager taskManager = new TaskManager(new StorageHandler(storagePath),
                new TimeTracker(Clock.systemUTC()));
        try {
            taskManager.load();
        } catch (StorageException exception) {
            renderer.fatal("[" + exception.getCode() + "] " + exception.getMessage());
            return;
        }

        renderer.welcome();
        CommandParser parser = new CommandParser(taskManager, renderer);
        Console nativeConsole = System.console();
        if (nativeConsole != null) {
            renderer.historyHint();
            runNativeConsole(nativeConsole, parser, renderer);
        } else {
            runBufferedInput(parser, renderer);
        }
    }

    private static void runNativeConsole(Console nativeConsole, CommandParser parser, ConsoleRenderer renderer) {
        while (true) {
            renderer.prompt();
            String line = nativeConsole.readLine();
            if (line == null || !parser.execute(line)) {
                return;
            }
        }
    }

    private static void runBufferedInput(CommandParser parser, ConsoleRenderer renderer) {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while (true) {
                renderer.prompt();
                line = input.readLine();
                if (line == null || !parser.execute(line)) break;
            }
        } catch (IOException exception) {
            renderer.fatal("A konzol bemenete nem olvasható.");
        } catch (RuntimeException exception) {
            renderer.unexpectedError();
        }
    }
}
