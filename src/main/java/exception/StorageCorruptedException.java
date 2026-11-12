package exception;

public class StorageCorruptedException extends StorageReadException {
    public StorageCorruptedException(ErrorCode code, String message) {
        super(code, message);
    }

    public StorageCorruptedException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
