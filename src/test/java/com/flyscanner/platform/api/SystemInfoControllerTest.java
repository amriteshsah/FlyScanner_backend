package com.flyscanner.platform.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemInfoControllerTest {

    private final SystemInfoController controller = new SystemInfoController();

    @Test
    void exposesBackendIdentityAndApiVersion() {
        SystemInfoController.SystemInfo result = controller.info();

        assertEquals("FlyScanner Backend", result.application());
        assertEquals("v1", result.apiVersion());
        assertEquals("UP", result.status());
    }
}
