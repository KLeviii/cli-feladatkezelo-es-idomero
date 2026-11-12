package persistence;

import exception.StorageException;

/** A feladatállapot tartós tárolásának szerződése. */
public interface TaskRepository {
    TaskData load() throws StorageException;

    void save(TaskData taskData) throws StorageException;
}
