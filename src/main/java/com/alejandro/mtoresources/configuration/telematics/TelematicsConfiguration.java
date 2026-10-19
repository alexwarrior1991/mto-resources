package com.alejandro.mtoresources.configuration.telematics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TelematicsProperties.class)
public class TelematicsConfiguration {
}
