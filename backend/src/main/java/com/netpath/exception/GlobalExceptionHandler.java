package com.netpath.exception;

import com.netpath.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Turns every failure into the same {@link ApiError} shape.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring keeps deciding the status code for the
 * malformed-request family it already classifies (unreadable body, unparseable parameter, unsupported
 * media type, unknown route, wrong method). Declaring a catch-all for {@link Exception} without this
 * parent relabelled all of those as 500; here only the body shape is ours, plus the three messages
 * where Spring's default text does not tell a caller what to fix.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequest(BadRequestException ex, WebRequest request) {
        logger.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Object> handleUnauthorized(UnauthorizedException ex, WebRequest request) {
        logger.warn("Unauthorized: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    /** Reports every violated field rather than only the first. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        logger.warn("Validation failed: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * A parameter or path variable that cannot be converted, most often a mistyped enum or id. Names
     * the offending value and, for enums, the accepted values.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        String message = "Invalid value: " + ex.getMessage();

        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            Class<?> target = mismatch.getRequiredType();
            String expected = target != null && target.isEnum()
                    ? " (expected one of " + Arrays.toString(target.getEnumConstants()) + ")"
                    : "";
            message = String.format("Invalid value '%s' for '%s'%s", mismatch.getValue(), mismatch.getName(), expected);
        }

        logger.warn("Type mismatch: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** Re-shapes everything else Spring already classified into the documented error body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());

        String message = body instanceof ProblemDetail detail && detail.getDetail() != null
                ? detail.getDetail()
                : status.getReasonPhrase();

        logger.warn("{} {}: {}", status.value(), status.getReasonPhrase(), message);
        return new ResponseEntity<>(apiError(status, message, request), headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        logger.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<Object> build(HttpStatus status, String message, WebRequest request) {
        return new ResponseEntity<>(apiError(status, message, request), status);
    }

    private ApiError apiError(HttpStatus status, String message, WebRequest request) {
        return new ApiError(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getDescription(false).replace("uri=", ""));
    }
}
