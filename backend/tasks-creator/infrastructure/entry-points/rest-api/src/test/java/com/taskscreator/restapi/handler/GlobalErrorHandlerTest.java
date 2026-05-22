package com.taskscreator.restapi.handler;

import com.taskscreator.model.exception.TaskNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@WebFluxTest(controllers = GlobalErrorHandlerTest.ThrowingController.class)
@Import(GlobalErrorHandler.class)
class GlobalErrorHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/test/not-found")
        Mono<String> throwNotFound() {
            return Mono.error(new TaskNotFoundException(UUID.randomUUID()));
        }

        @GetMapping("/test/illegal")
        Mono<String> throwIllegal() {
            return Mono.error(new IllegalArgumentException("bad arg"));
        }

        @GetMapping("/test/generic")
        Mono<String> throwGeneric() {
            return Mono.error(new RuntimeException("boom"));
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    // -------------------------------------------------------------------------
    // TC-EH-01: TaskNotFoundException → 404
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-EH-01: TaskNotFoundException debe retornar 404 con ErrorResponse")
    void taskNotFoundException_shouldReturn404WithErrorResponse() {
        webTestClient.get()
                .uri("/test/not-found")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // TC-EH-02: IllegalArgumentException → 400
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-EH-02: IllegalArgumentException debe retornar 400 con ErrorResponse")
    void illegalArgumentException_shouldReturn400WithErrorResponse() {
        webTestClient.get()
                .uri("/test/illegal")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.message").isEqualTo("bad arg")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // TC-EH-03: Generic Exception → 500
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-EH-03: Exception genérica debe retornar 500 con ErrorResponse")
    void genericException_shouldReturn500WithErrorResponse() {
        webTestClient.get()
                .uri("/test/generic")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.error").isEqualTo("Internal Server Error")
                .jsonPath("$.message").isEqualTo("boom")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // TC-EH-04: ErrorResponse timestamp is present and non-null
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-EH-04: ErrorResponse debe contener timestamp no nulo")
    void errorResponse_shouldContainNonNullTimestamp() {
        webTestClient.get()
                .uri("/test/not-found")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.timestamp").isNotEmpty();
    }
}
