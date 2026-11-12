package exception;

public class TimeCalculationException extends TaskStateException {
    public TimeCalculationException(ErrorCode code, String message) {
        super(code, message);
    }
}
