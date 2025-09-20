package com.nsebot.exception;

/**
 * Exception thrown when stock analysis operations fail
 */
public class StockAnalysisException extends RuntimeException {
    
    private final String symbol;
    private final String analysisType;
    
    public StockAnalysisException(String message, String symbol) {
        super(message);
        this.symbol = symbol;
        this.analysisType = "GENERAL";
    }
    
    public StockAnalysisException(String message, String symbol, String analysisType) {
        super(message);
        this.symbol = symbol;
        this.analysisType = analysisType;
    }
    
    public StockAnalysisException(String message, String symbol, Throwable cause) {
        super(message, cause);
        this.symbol = symbol;
        this.analysisType = "GENERAL";
    }
    
    public StockAnalysisException(String message, String symbol, String analysisType, Throwable cause) {
        super(message, cause);
        this.symbol = symbol;
        this.analysisType = analysisType;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public String getAnalysisType() {
        return analysisType;
    }
}