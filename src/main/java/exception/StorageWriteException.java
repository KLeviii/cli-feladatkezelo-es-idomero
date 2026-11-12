package exception;

public class StorageWriteException extends StorageException {
    public StorageWriteException(ErrorCode code, String message) {
        super(code, message);
    }

    public StorageWriteException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
