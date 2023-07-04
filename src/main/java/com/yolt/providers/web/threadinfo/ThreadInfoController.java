package com.yolt.providers.web.threadinfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-point for getting thread info.
 */
@Slf4j
@RestController
@RequestMapping(value = "thread-info", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
class ThreadInfoController {

    private final ThreadInfoService threadInfoService;

    @GetMapping
    public ResponseEntity<Void> getThreadsInfo(@RequestParam(required = false) String regex) {
        threadInfoService.logQuasiThreadDump(regex);
        return ResponseEntity
                .ok()
                .build();
    }
}