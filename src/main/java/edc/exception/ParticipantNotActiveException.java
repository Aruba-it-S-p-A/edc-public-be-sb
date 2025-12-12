package edc.exception;

public class ParticipantNotActiveException extends RuntimeException {

    public ParticipantNotActiveException(String message) {
        super(message);
    }

    public ParticipantNotActiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
