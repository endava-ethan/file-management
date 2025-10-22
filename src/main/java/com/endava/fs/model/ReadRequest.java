package com.endava.fs.model;

import jakarta.validation.constraints.NotBlank;

public record ReadRequest(
        @NotBlank(message = "Path is required")
        String path,
        Boolean base64
) {
}
