package com.endava.fs.controller;

import com.endava.fs.model.FileEntry;
import com.endava.fs.model.ReadRequest;
import com.endava.fs.model.ReadResult;
import com.endava.fs.model.WriteRequest;
import com.endava.fs.model.WriteResult;
import com.endava.fs.service.FsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/fs")
@Validated
public class FsController {

    private final FsService fsService;

    public FsController(FsService fsService) {
        this.fsService = fsService;
    }

    @GetMapping("/list")
    @Operation(summary = "List files under the root directory using a glob pattern")
    public Mono<List<FileEntry>> list(@RequestParam(name = "glob", defaultValue = "**/*") String glob) {
        return fsService.list(glob);
    }

    @PostMapping("/read")
    @Operation(summary = "Read a file with optional base64 encoding")
    public Mono<ReadResult> read(@Valid @RequestBody ReadRequest request) {
        return fsService.read(request);
    }

    @PostMapping("/write")
    @Operation(summary = "Write or append file content with atomic semantics")
    public Mono<WriteResult> write(@Valid @RequestBody WriteRequest request) {
        return fsService.write(request);
    }
}
