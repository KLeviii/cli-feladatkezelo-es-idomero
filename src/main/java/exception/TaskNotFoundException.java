package exception;

public class TaskNotFoundException extends ApplicationException {
    public TaskNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }
}
