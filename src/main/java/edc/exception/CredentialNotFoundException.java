package edc.exception;

public class CredentialNotFoundException extends RuntimeException {
    
    public CredentialNotFoundException(String message) {
        super(message);
    }
    
    public CredentialNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
