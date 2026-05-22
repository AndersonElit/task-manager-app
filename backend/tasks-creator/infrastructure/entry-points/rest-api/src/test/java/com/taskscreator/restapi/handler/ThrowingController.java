package com.taskscreator.restapi.handler;

import com.taskscreator.model.exception.TaskNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
class ThrowingController {

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
