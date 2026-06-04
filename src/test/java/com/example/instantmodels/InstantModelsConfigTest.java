package com.example.instantmodels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstantModelsConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void requiredValueTrimsConfiguredEndpoint() {
        assertEquals("https://example.services.ai.azure.com/api/projects/demo",
                InstantModelsConfig.requiredValue("FOUNDRY_PROJECT_ENDPOINT",
                        "  https://example.services.ai.azure.com/api/projects/demo  "));
    }

    @Test
    void requiredValueRejectsBlankEndpoint() {
        assertThrows(IllegalStateException.class,
                () -> InstantModelsConfig.requiredValue("FOUNDRY_PROJECT_ENDPOINT", " "));
    }

    @Test
    void defaultModelUsesInstantModelName() {
        assertEquals("gpt-chat-latest", InstantModelsConfig.DEFAULT_MODEL);
    }

    @Test
    void optionalValueFallsBackWhenBlank() {
        assertEquals("fallback", InstantModelsConfig.valueOrDefault(" ", "fallback"));
    }

    @Test
    void defaultPricingMeterPrefixMapsChatAlias() {
        assertEquals("5.5 ShortCo", InstantModelsConfig.defaultPricingMeterPrefix("gpt-chat-latest"));
    }

    @Test
    void defaultPricingMeterPrefixMapsLongContextName() {
        assertEquals("5.5 LongCo", InstantModelsConfig.defaultPricingMeterPrefix("gpt-5.5-long"));
    }

    @Test
    void defaultPricingMeterPrefixMapsMiniName() {
        assertEquals("5 mini", InstantModelsConfig.defaultPricingMeterPrefix("gpt-5-mini"));
    }

    @Test
    void loadDotenvReadsExpectedValues() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, String.join(System.lineSeparator(),
                "# local settings",
                "FOUNDRY_PROJECT_ENDPOINT=https://example.services.ai.azure.com/api/projects/demo",
                "export FOUNDRY_MODEL=\"gpt-chat-latest\"",
                "FOUNDRY_PROMPT='Explain instant models.'"));

        assertEquals("https://example.services.ai.azure.com/api/projects/demo",
                InstantModelsConfig.loadDotenv(dotenv).get("FOUNDRY_PROJECT_ENDPOINT"));
        assertEquals("gpt-chat-latest", InstantModelsConfig.loadDotenv(dotenv).get("FOUNDRY_MODEL"));
        assertEquals("Explain instant models.", InstantModelsConfig.loadDotenv(dotenv).get("FOUNDRY_PROMPT"));
    }
}