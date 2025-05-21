package org.wso2;

/**
 * Exception class for Authzen security exceptions.
 */
public class AuthzenSecurityException extends Exception {

    public static final int INTERNAL_ERROR = 902200;
    public static final String INTERNAL_ERROR_MESSAGE = "Unexpected Authzen mediator failure";
    public static final int ACCESS_REVOKED = 902201;
    public static final String ACCESS_REVOKED_MESSAGE = "Request validation failure at the remote authorization server";

    private int errorCode;

    public AuthzenSecurityException(String message) {

        super(message);
    }

    public AuthzenSecurityException(int errorCode, String message) {

        super(message);
        this.errorCode = errorCode;
    }

    public AuthzenSecurityException(int errorCode, String message, Throwable cause) {

        super(message, cause);
        this.errorCode = errorCode;
    }

    public AuthzenSecurityException(String message, Throwable cause) {

        super(message, cause);
    }

    public AuthzenSecurityException(Throwable cause) {

        super(cause.getMessage(), cause);
    }

    public int getErrorCode() {

        return errorCode;
    }
}
