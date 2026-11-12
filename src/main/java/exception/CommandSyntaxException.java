package exception;

public final class CommandSyntaxException extends ApplicationException {
    public CommandSyntaxException(ErrorCode code, String message) {
        super(code, message);
    }
}