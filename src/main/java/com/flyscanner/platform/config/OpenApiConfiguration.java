package com.flyscanner.platform.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        servers = @Server(url = "/", description = "Same-origin API server"),
        info = @Info(
                title = "FlyScanner API",
                version = "v1",
                description = "REST API for FlyScanner account authentication, flight search and price alerts. "
                        + "Flight offers currently come from a synthetic mock supplier and are not bookable."
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "Opaque access token"
)
public class OpenApiConfiguration {
}
