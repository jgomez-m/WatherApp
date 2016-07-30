package com.crossover.trial.weather.exception;

/**
 * An internal exception marker
 */
public class WeatherException extends Exception {
    
    public WeatherException(String message, Throwable cause) {
        super(message, cause);
    }
}
