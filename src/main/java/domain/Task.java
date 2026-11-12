package domain;

import exception.ErrorCode;
import exception.TaskStateException;
import exception.TimeCalculationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Task {
    private final long id;
    private final String title;
    private TaskStatus status;
    private long accumulatedSeconds;
    private Instant startedAt;

    public Task(long id, String title) {
        this(id, title, TaskStatus.TODO, 0, null);
    }

    public Task(long id, String title, TaskStatus status, long accumulatedSeconds, Instant startedAt) {
        if (id <= 0) {
            throw new IllegalArgumentException("A feladat ID-ja pozitív legyen.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A feladat címe nem lehet üres.");
        }
        if (accumulatedSeconds < 0) {
            throw new IllegalArgumentException("A felhalmozott idő nem lehet negatív.");
        }
        this.id = id;
        this.title = title;
        this.status = Objects.requireNonNull(status, "A státusz nem lehet null.");
        this.accumulatedSeconds = accumulatedSeconds;
        this.startedAt = startedAt;
        validatePersistedState();
    }

    private void validatePersistedState() {
        if (status == TaskStatus.IN_PROGRESS && startedAt == null) {
            throw new IllegalArgumentException("Az aktív feladathoz kezdő időpont szükséges.");
        }
        if (status != TaskStatus.IN_PROGRESS && startedAt != null) {
            throw new IllegalArgumentException("Csak aktív feladatnak lehet kezdő időpontja.");
        }
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public long getAccumulatedSeconds() {
        return accumulatedSeconds;
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public void start(Instant startTime) throws TaskStateException {
        if (status != TaskStatus.TODO) {
            throw new TaskStateException(ErrorCode.TASK_INVALID_TRANSITION,
                    "Csak TODO állapotú feladat indítható.");
        }
        startedAt = Objects.requireNonNull(startTime, "A kezdő időpont nem lehet null.");
        status = TaskStatus.IN_PROGRESS;
    }

    public long stop(Instant stopTime) throws TaskStateException {
        if (status != TaskStatus.IN_PROGRESS || startedAt == null) {
            throw new TaskStateException(ErrorCode.TASK_NOT_RUNNING, "Ez a feladat nem fut.");
        }
        Instant end = Objects.requireNonNull(stopTime, "A leállítási idő nem lehet null.");
        long elapsed = Duration.between(startedAt, end).getSeconds();
        if (elapsed < 0) {
            throw new TimeCalculationException(ErrorCode.TIME_CLOCK_REGRESSION,
                    "Az óra visszaállt; negatív idő nem számolható el.");
        }
        accumulatedSeconds += elapsed;
        startedAt = null;
        status = TaskStatus.TODO;
        return elapsed;
    }

    public void markTodo() throws TaskStateException {
        if (status == TaskStatus.IN_PROGRESS) {
            throw new TaskStateException(ErrorCode.TASK_INVALID_TRANSITION,
                    "Futó feladatot előbb le kell állítani.");
        }
        status = TaskStatus.TODO;
    }

    public void markDone() throws TaskStateException {
        if (status == TaskStatus.IN_PROGRESS) {
            throw new TaskStateException(ErrorCode.TASK_INVALID_TRANSITION,
                    "Futó feladatot előbb le kell állítani.");
        }
        status = TaskStatus.DONE;
    }
}
