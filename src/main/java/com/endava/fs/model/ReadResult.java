package com.endava.fs.model;

public record ReadResult(
        String path,
        String content,
        boolean base64
) {
}
