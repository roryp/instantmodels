package com.example.instantmodels;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

final class RetailPricingClient {
    private static final String RETAIL_PRICES_ENDPOINT = "https://prices.azure.com/api/retail/prices";
    private static final String API_VERSION = "2023-01-01-preview";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    RetailPricingClient() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    RetailPricingClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    ModelPricing getPricing(String selectedModel, String meterPrefix, String region, String currencyCode, String scope) {
        List<RetailPriceItem> items = fetchPricingItems(meterPrefix, region, currencyCode);
        RetailPriceItem input = findRequiredMeter(items, meterPrefix, scope, "standard input", this::isStandardInputMeter);
        RetailPriceItem output = findRequiredMeter(items, meterPrefix, scope, "output", this::isOutputMeter);
        Optional<RetailPriceItem> cachedInput = findMeter(items, meterPrefix, scope, this::isCachedInputMeter);

        return new ModelPricing(selectedModel, meterPrefix, region, currencyCode,
                toMeter(input), cachedInput.map(this::toMeter), toMeter(output), Instant.now());
    }

    private List<RetailPriceItem> fetchPricingItems(String meterPrefix, String region, String currencyCode) {
        String filter = "serviceName eq 'Foundry Models' and priceType eq 'Consumption'"
                + " and armRegionName eq '" + escapeOData(region) + "'"
                + " and contains(meterName, '" + escapeOData(meterPrefix) + "')";
        String query = "api-version=" + encode(API_VERSION)
                + "&currencyCode='" + encode(currencyCode) + "'"
                + "&$filter=" + encode(filter);

        List<RetailPriceItem> items = new ArrayList<>();
        String nextPage = RETAIL_PRICES_ENDPOINT + "?" + query;
        while (nextPage != null && !nextPage.isBlank()) {
            RetailPricesResponse response = getPage(nextPage);
            if (response.items() != null) {
                items.addAll(response.items());
            }
            nextPage = response.nextPageLink();
        }

        if (items.isEmpty()) {
            throw new IllegalStateException("No live Azure Retail Prices meters found for meter prefix '"
                    + meterPrefix + "' in region '" + region + "'.");
        }

        return items;
    }

    private RetailPricesResponse getPage(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Azure Retail Prices API returned HTTP " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), RetailPricesResponse.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to call Azure Retail Prices API", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Azure Retail Prices API", ex);
        }
    }

    private RetailPriceItem findRequiredMeter(
            List<RetailPriceItem> items,
            String meterPrefix,
            String scope,
            String meterKind,
            Predicate<String> meterNamePredicate) {
        return findMeter(items, meterPrefix, scope, meterNamePredicate)
                .orElseThrow(() -> new IllegalStateException("No live Azure Retail Prices " + meterKind
                        + " meter found for prefix '" + meterPrefix + "' and scope '" + scope + "'."));
    }

    private Optional<RetailPriceItem> findMeter(
            List<RetailPriceItem> items,
            String meterPrefix,
            String scope,
            Predicate<String> meterNamePredicate) {
        return items.stream()
                .filter(item -> item.unitPrice() != null)
                .filter(item -> item.meterName() != null)
                .filter(item -> containsIgnoreCase(item.meterName(), meterPrefix))
                .filter(item -> containsWord(item.meterName(), scope))
                .filter(item -> !containsWord(item.meterName(), "Batch"))
                .filter(item -> !containsWord(item.meterName(), "PP"))
                .filter(item -> meterNamePredicate.test(item.meterName()))
                .min(Comparator.comparing(RetailPriceItem::meterName));
    }

    private boolean isStandardInputMeter(String meterName) {
        return containsWord(meterName, "inp")
                && !containsSequence(meterName, "cd inp")
                && !containsWord(meterName, "opt");
    }

    private boolean isCachedInputMeter(String meterName) {
        return containsSequence(meterName, "cd inp");
    }

    private boolean isOutputMeter(String meterName) {
        return containsWord(meterName, "opt");
    }

    private RetailPriceMeter toMeter(RetailPriceItem item) {
        return new RetailPriceMeter(item.meterName(), item.unitPrice(), item.currencyCode(), item.unitOfMeasure());
    }

    private static boolean containsIgnoreCase(String text, String value) {
        return text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    private static boolean containsWord(String text, String word) {
        if (word == null || word.isBlank()) {
            return true;
        }

        String normalizedText = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]+", " ") + " ";
        String normalizedWord = " " + word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]+", " ").trim() + " ";
        return normalizedText.contains(normalizedWord);
    }

    private static boolean containsSequence(String text, String value) {
        String normalizedText = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]+", " ").trim();
        String normalizedValue = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]+", " ").trim();
        return normalizedText.contains(normalizedValue);
    }

    private static String escapeOData(String value) {
        return value.replace("'", "''");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RetailPricesResponse(
            @JsonProperty("Items") List<RetailPriceItem> items,
            @JsonProperty("NextPageLink") String nextPageLink) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RetailPriceItem(
            String meterName,
            BigDecimal unitPrice,
            String currencyCode,
            String unitOfMeasure) {
    }
}