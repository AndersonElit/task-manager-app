package com.taskscreator;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Registra los casos de uso como beans de Spring.
 * Los casos de uso son clases Java puras sin anotaciones de Spring,
 * por eso se registran explícitamente aquí (no los detecta @SpringBootApplication).
 * Los adaptadores (@Component, @Configuration) los levanta @SpringBootApplication.
 */
@Configuration
@ComponentScan(
        basePackages = {"com.taskscreator.usecases"},
        includeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = ".*UseCase$"
                )
        },
        useDefaultFilters = false
)
public class ApplicationConfig {
}
