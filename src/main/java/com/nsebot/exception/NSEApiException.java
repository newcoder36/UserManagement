package com.nsebot.exception;

/**
 * Exception thrown when NSE API operations fail
 */
public class NSEApiException extends RuntimeException {
    
    private final String errorCode;
    
    public NSEApiException(String message) {
        super(message);
        this.errorCode = "NSE_API_ERROR";
    }
    
    public NSEApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public NSEApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "NSE_API_ERROR";
    }
    
    public NSEApiException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}