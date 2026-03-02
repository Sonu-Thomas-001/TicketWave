package com.ticketwave.common.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String errorCode;
    private String message;
    private String path;
}
