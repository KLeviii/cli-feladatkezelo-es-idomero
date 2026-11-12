package exception;

import java.util.Objects;

public abstract class ApplicationException extends Exception {
    private final ErrorCode code;

    protected ApplicationException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code);
    }

    protected ApplicationException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code);
    }

    public ErrorCode getCode() {
        return code;
    }
}