package com.tasksprocessor;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Registra los casos de uso como beans de Spring.
 * Los adaptadores y consumidores (@Component, @Configuration)
 * los detecta @SpringBootApplication por estar en subpaquetes de com.tasksprocessor.
 */
@Configuration
@ComponentScan(
        basePackages = {"com.tasksprocessor.usecases"},
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
