package com.example.instantmodels;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class InstantModelsConfig {
    private static final String PROJECT_ENDPOINT_ENV = "FOUNDRY_PROJECT_ENDPOINT";
    private static final String MODEL_ENV = "FOUNDRY_MODEL";
    private static final String PROMPT_ENV = "FOUNDRY_PROMPT";
    private static final String PRICING_REGION_ENV = "FOUNDRY_PRICING_REGION";
    private static final String PRICING_CURRENCY_ENV = "FOUNDRY_PRICING_CURRENCY";
    private static final String PRICING_SCOPE_ENV = "FOUNDRY_PRICING_SCOPE";
    private static final String PRICING_METER_PREFIX_ENV = "FOUNDRY_PRICING_METER_PREFIX";
    private static final Map<String, String> DOTENV = loadDotenv(Path.of(".env"));

    static final String DEFAULT_MODEL = "gpt-chat-latest";
    static final String DEFAULT_PROMPT = "In one sentence, what are instant models in Microsoft Foundry?";
    static final String DEFAULT_PRICING_REGION = "westus3";
    static final String DEFAULT_PRICING_CURRENCY = "USD";
    static final String DEFAULT_PRICING_SCOPE = "Gl";

    private InstantModelsConfig() {
    }

    static String projectEndpoint() {
        return requiredValue(PROJECT_ENDPOINT_ENV, configuredValue(PROJECT_ENDPOINT_ENV));
    }

    static String model() {
        return valueOrDefault(configuredValue(MODEL_ENV), DEFAULT_MODEL);
    }

    static String prompt() {
        return valueOrDefault(configuredValue(PROMPT_ENV), DEFAULT_PROMPT);
    }

    static String pricingRegion() {
        return valueOrDefault(configuredValue(PRICING_REGION_ENV), DEFAULT_PRICING_REGION).toLowerCase(Locale.ROOT);
    }

    static String pricingCurrency() {
        return valueOrDefault(configuredValue(PRICING_CURRENCY_ENV), DEFAULT_PRICING_CURRENCY).toUpperCase(Locale.ROOT);
    }

    static String pricingScope() {
        return valueOrDefault(configuredValue(PRICING_SCOPE_ENV), DEFAULT_PRICING_SCOPE);
    }

    static String pricingMeterPrefix() {
        return valueOrDefault(configuredValue(PRICING_METER_PREFIX_ENV), defaultPricingMeterPrefix(model()));
    }

    static String defaultPricingMeterPrefix(String model) {
        String normalized = model.toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.equals("gpt-chat-latest") || normalized.equals("gpt-5.5")) {
            return "5.5 ShortCo";
        }

        if (normalized.startsWith("gpt-5.5") && normalized.contains("long")) {
            return "5.5 LongCo";
        }

        if (normalized.startsWith("gpt-5.5")) {
            return "5.5 ShortCo";
        }

        if (normalized.startsWith("gpt-5-mini")) {
            return "5 mini";
        }

        if (normalized.startsWith("gpt-5")) {
            return "GPT 5";
        }

        return model.replace('-', ' ');
    }

    static Map<String, String> loadDotenv(Path path) {
        if (!Files.isRegularFile(path)) {
            return Collections.emptyMap();
        }

        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                parseDotenvLine(line).forEach(values::put);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read " + path, ex);
        }

        return Collections.unmodifiableMap(values);
    }

    private static Map<String, String> parseDotenvLine(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Collections.emptyMap();
        }

        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring("export ".length()).strip();
        }

        int separator = trimmed.indexOf('=');
        if (separator <= 0) {
            return Collections.emptyMap();
        }

        String name = trimmed.substring(0, separator).strip();
        String value = unquote(trimmed.substring(separator + 1).strip());
        if (name.isEmpty()) {
            return Collections.emptyMap();
        }

        return Map.of(name, value);
    }

    private static String configuredValue(String name) {
        String environmentValue = System.getenv(name);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        return DOTENV.get(name);
    }

    static String requiredValue(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + name + " to your Foundry project endpoint, for example "
                    + "https://<resource-name>.services.ai.azure.com/api/projects/<project-name>");
        }

        return value.trim();
    }

    static String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }

        return value;
    }
}