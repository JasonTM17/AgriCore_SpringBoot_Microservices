package com.agricore.assistant.application.port;

import java.time.Duration;

public interface ChatGenerationPolicy {

    String model();

    int maxInputCharacters();

    int maxOutputTokens();

    double temperature();

    Duration maxGenerationDuration();
}
