package edc.exception;

public class ParticipantConflictException extends RuntimeException {
    
    public ParticipantConflictException(String message) {
        super(message);
    }
    
    public ParticipantConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
