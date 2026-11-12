package com.example.payment_api;

public class InvalidCallbackSecretException extends RuntimeException {

    public InvalidCallbackSecretException() {
        super("Invalid callback secret");
    }
}