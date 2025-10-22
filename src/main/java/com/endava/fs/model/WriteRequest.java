package com.endava.fs.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WriteRequest(
        @NotBlank(message = "Path is required")
        String path,
        @NotNull(message = "Content is required")
        String content,
        boolean base64,
        @NotNull(message = "Mode is required")
        WriteMode mode
) {
}
