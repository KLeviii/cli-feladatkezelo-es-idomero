package exception;

public class TaskStateException extends ApplicationException {
    public TaskStateException(ErrorCode code, String message) {
        super(code, message);
    }
}
