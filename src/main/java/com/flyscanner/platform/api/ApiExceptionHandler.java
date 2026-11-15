package com.flyscanner.platform.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import com.flyscanner.account.application.AccountAuthenticationService;
import com.flyscanner.alerts.application.PriceAlertService;
import com.flyscanner.saved.application.SavedFlightService;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
                "Invalid email or password, or the access token is invalid or expired.", request);
    }

    @ExceptionHandler(AccountAuthenticationService.EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateAccount(
            AccountAuthenticationService.EmailAlreadyRegisteredException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Account already exists", exception.getMessage(), request);
    }

    @ExceptionHandler(PriceAlertService.AlertNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAlertNotFound(
            PriceAlertService.AlertNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Price alert not found", exception.getMessage(), request);
    }

    @ExceptionHandler(SavedFlightService.SavedOfferNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleSavedOfferNotFound(
            SavedFlightService.SavedOfferNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Flight offer not found", exception.getMessage(), request);
    }

    @ExceptionHandler(SavedFlightService.SavedFlightNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleSavedFlightNotFound(
            SavedFlightService.SavedFlightNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Saved flight not found", exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        log.error("Unhandled request failure for path {}", request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "The request could not be completed. Use the correlation ID when contacting support.", request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        body.setTitle(title);
        body.setInstance(URI.create(request.getRequestURI()));
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            body.setProperty("correlationId", correlationId);
        }
        return ResponseEntity.status(status).body(body);
    }
}
