package com.endava.fs.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;

@Validated
@ConfigurationProperties("fs")
public record FsProperties(
        @NotNull Path rootDir,
        @NotEmpty List<String> allow
) {

    public Path root() {
        return rootDir.toAbsolutePath().normalize();
    }
}
