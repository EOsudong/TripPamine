package com.example.trippaminebe.domain.travel.exception;

public class TravelPlanNotFoundException extends RuntimeException {

    public TravelPlanNotFoundException(String message) {
        super(message);
    }
}