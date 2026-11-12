package service;

import domain.Task;
import domain.TaskStatus;
import exception.ErrorCode;
import exception.StorageException;
import exception.TaskNotFoundException;
import exception.TaskStateException;
import exception.ValidationException;
import persistence.TaskData;
import persistence.TaskRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Üzleti szabályok, az egyetlen aktív feladat invariánsával. */
public final class TaskManager {
    private static final int MAX_TITLE_LENGTH = 200;

    private final Map<Long, Task> tasks = new LinkedHashMap<>();
    private final TaskRepository repository;
    private final TimeTracker timeTracker;
    private long nextId = 1;
    private boolean unsavedChanges;

    public TaskManager(TaskRepository repository, TimeTracker timeTracker) {
        this.repository = Objects.requireNonNull(repository, "A repository nem lehet null.");
        this.timeTracker = Objects.requireNonNull(timeTracker, "A TimeTracker nem lehet null.");
    }

    public void load() throws StorageException {
        TaskData data = repository.load();
        tasks.clear();
        long activeCount = 0;
        long maxId = 0;
        for (Task task : data.tasks()) {
            if (tasks.putIfAbsent(task.getId(), task) != null) {
                throw new StorageException(ErrorCode.STORAGE_INVALID_STATE,
                        "A mentési fájl ismétlődő feladat-ID-t tartalmaz.");
            }
            if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                activeCount++;
            }
            maxId = Math.max(maxId, task.getId());
        }
        if (activeCount > 1 || data.nextId() <= maxId) {
            throw new StorageException(ErrorCode.STORAGE_INVALID_STATE,
                    "A mentési fájl feladatállapota érvénytelen.");
        }
        nextId = data.nextId();
        unsavedChanges = false;
    }

    public Task addTask(String rawTitle) throws ValidationException, StorageException {
        String title = validateTitle(rawTitle);
        Task task = new Task(nextId++, title);
        tasks.put(task.getId(), task);
        persistAfterChange();
        return task;
    }

    public List<Task> listTasks() {
        return tasks.values().stream()
                .sorted(Comparator.comparingLong(Task::getId))
                .toList();
    }

    public Task startTask(long id) throws TaskNotFoundException, TaskStateException, StorageException {
        Task target = requireTask(id);
        Optional<Task> active = getActiveTask();
        if (active.isPresent() && active.get().getId() != id) {
            throw new TaskStateException(ErrorCode.TASK_ALREADY_RUNNING,
                    "Már fut egy feladat: " + active.get().getId() + ". Előbb állítsd le.");
        }
        target.start(timeTracker.now());
        persistAfterChange();
        return target;
    }

    public long stopTask(long id) throws TaskNotFoundException, TaskStateException, StorageException {
        Task task = requireTask(id);
        long elapsed = task.stop(timeTracker.now());
        persistAfterChange();
        return elapsed;
    }

    public Task changeStatus(long id, TaskStatus requested)
            throws TaskNotFoundException, TaskStateException, StorageException {
        Task task = requireTask(id);
        switch (requested) {
            case IN_PROGRESS -> {
                Optional<Task> active = getActiveTask();
                if (active.isPresent() && active.get().getId() != id) {
                    throw new TaskStateException(ErrorCode.TASK_ALREADY_RUNNING,
                            "Már fut egy feladat: " + active.get().getId() + ". Előbb állítsd le.");
                }
                task.start(timeTracker.now());
            }
            case TODO -> {
                if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                    task.stop(timeTracker.now());
                } else {
                    task.markTodo();
                }
            }
            case DONE -> {
                if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                    task.stop(timeTracker.now());
                }
                task.markDone();
            }
        }
        persistAfterChange();
        return task;
    }

    public void deleteTask(long id) throws TaskNotFoundException, TaskStateException, StorageException {
        Task task = requireTask(id);
        if (task.getStatus() == TaskStatus.IN_PROGRESS) {
            throw new TaskStateException(ErrorCode.TASK_DELETE_ACTIVE,
                    "Futó feladatot nem lehet törölni. Előbb állítsd le.");
        }
        tasks.remove(id);
        persistAfterChange();
    }

    public Optional<Task> getActiveTask() {
        return tasks.values().stream().filter(task -> task.getStatus() == TaskStatus.IN_PROGRESS).findFirst();
    }

    public boolean hasUnsavedChanges() {
        return unsavedChanges;
    }

    public void savePending() throws StorageException {
        repository.save(new TaskData(nextId, new ArrayList<>(tasks.values())));
        unsavedChanges = false;
    }

    private void persistAfterChange() throws StorageException {
        unsavedChanges = true;
        savePending();
    }

    private Task requireTask(long id) throws TaskNotFoundException {
        Task task = tasks.get(id);
        if (task == null) {
            throw new TaskNotFoundException(ErrorCode.TASK_NOT_FOUND, "Nincs ilyen feladat: " + id + ".");
        }
        return task;
    }

    private String validateTitle(String rawTitle) throws ValidationException {
        if (rawTitle == null || rawTitle.isBlank()) {
            throw new ValidationException(ErrorCode.VALIDATION_TITLE, "A feladat címe nem lehet üres.");
        }
        String title = rawTitle.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ValidationException(ErrorCode.VALIDATION_TITLE,
                    "A feladat címe legfeljebb " + MAX_TITLE_LENGTH + " karakter lehet.");
        }
        return title;
    }
}
