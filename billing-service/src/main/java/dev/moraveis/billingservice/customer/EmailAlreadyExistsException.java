package dev.moraveis.billingservice.customer;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(Throwable cause) {
        super("Email already exists", cause);
    }
}
