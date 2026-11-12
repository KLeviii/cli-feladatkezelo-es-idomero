package persistence;

import domain.Task;

import java.util.List;

/** A teljes, egy fájlba mentett alkalmazásállapot. */
public record TaskData(long nextId, List<Task> tasks) {
    public TaskData {
        if (nextId <= 0) {
            throw new IllegalArgumentException("A következő ID pozitív legyen.");
        }
        tasks = List.copyOf(tasks);
    }

    public static TaskData empty() {
        return new TaskData(1, List.of());
    }
}
