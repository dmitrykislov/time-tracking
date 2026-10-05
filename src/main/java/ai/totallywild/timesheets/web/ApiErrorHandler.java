package ai.totallywild.timesheets.web;

import ai.totallywild.timesheets.domain.TimesheetException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns rule violations and malformed input into a 400 whose {@code detail} the UI shows verbatim. */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(TimesheetException.class)
    public ProblemDetail rule(TimesheetException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException e) {
        var cause = rootCause(e);
        var detail = cause instanceof TimesheetException ? cause.getMessage() : "The request could not be read: " + cause.getMessage();
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail badPath(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid value '" + e.getValue() + "' for " + e.getName());
    }

    private static Throwable rootCause(Throwable t) {
        var current = t;
        while (current.getCause() != null && current.getCause() != current) {
            if (current instanceof TimesheetException) {
                return current;
            }
            current = current.getCause();
        }
        return current;
    }
}
