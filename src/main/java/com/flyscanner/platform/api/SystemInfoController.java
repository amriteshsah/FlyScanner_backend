package com.flyscanner.platform.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal endpoint to verify that the FlyScanner API is reachable. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    @GetMapping("/info")
    public SystemInfo info() {
        return new SystemInfo("FlyScanner Backend", "v1", "UP");
    }

    public record SystemInfo(String application, String apiVersion, String status) {
    }
}
