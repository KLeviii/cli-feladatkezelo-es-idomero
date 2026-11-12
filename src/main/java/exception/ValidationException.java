package exception;

public class ValidationException extends ApplicationException {
    public ValidationException(ErrorCode code, String message) {
        super(code, message);
    }
}
