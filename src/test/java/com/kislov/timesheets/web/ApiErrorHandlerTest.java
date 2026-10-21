package com.kislov.timesheets.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kislov.timesheets.domain.TimesheetException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

class ApiErrorHandlerTest {

    final ApiErrorHandler handler = new ApiErrorHandler();
    final MockHttpInputMessage message = new MockHttpInputMessage(new byte[0]);

    @Test
    void aRuleBuriedInTheCauseChainIsSurfacedVerbatim() {
        var rule = new TimesheetException("Finish (09:00) is before start (17:00)");
        var wrapped = new HttpMessageNotReadableException("JSON parse error", new RuntimeException("instantiation failed", rule), message);
        var problem = handler.unreadable(wrapped);
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Finish (09:00) is before start (17:00)");
    }

    @Test
    void aRuleInTheMiddleOfTheChainWinsOverDeeperCauses() {
        var rule = new TimesheetException("Break finish (12:00) is before its start (12:45)");
        rule.initCause(new IllegalArgumentException("deeper, irrelevant"));
        var wrapped = new HttpMessageNotReadableException("JSON parse error", new RuntimeException("instantiation failed", rule), message);
        assertThat(handler.unreadable(wrapped).getDetail()).isEqualTo("Break finish (12:00) is before its start (12:45)");
    }

    @Test
    void aPlainParseErrorIsExplainedWithTheRootCause() {
        var wrapped = new HttpMessageNotReadableException("JSON parse error", new RuntimeException("outer", new IllegalStateException("unexpected token")), message);
        assertThat(handler.unreadable(wrapped).getDetail()).isEqualTo("The request could not be read: unexpected token");

        var bare = new HttpMessageNotReadableException("no body", message);
        assertThat(handler.unreadable(bare).getDetail()).startsWith("The request could not be read: ");
    }

    @Test
    void aRuleViolationBecomesA400WithItsMessage() {
        var problem = handler.rule(new TimesheetException("Already on a break"));
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Already on a break");
    }
}
