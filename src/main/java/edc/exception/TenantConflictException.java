package edc.exception;

public class TenantConflictException extends RuntimeException {
    
    public TenantConflictException(String message) {
        super(message);
    }
    
    public TenantConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
