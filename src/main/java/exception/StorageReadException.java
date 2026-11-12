package exception;

public class StorageReadException extends StorageException {
    public StorageReadException(ErrorCode code, String message) {
        super(code, message);
    }

    public StorageReadException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
