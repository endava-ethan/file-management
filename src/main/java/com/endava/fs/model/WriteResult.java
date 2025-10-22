package com.endava.fs.model;

public record WriteResult(
        String path,
        int bytes,
        String mode
) {
}
