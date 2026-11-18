package com.flyscanner.sabre;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SabreProperties.class)
public class SabreConfiguration {
}
