package com.matriz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Matriz: the single deployable unit of the modular monolith (docs/architecture.md, Decision).
 * Each sub-package is one module (CMP-xx) and is used by other modules only through its public interface.
 */
@SpringBootApplication
public class MatrizApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatrizApplication.class, args);
    }
}
