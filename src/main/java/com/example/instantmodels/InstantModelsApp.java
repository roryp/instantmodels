package com.example.instantmodels;

import com.azure.ai.agents.AgentsClientBuilder;
import com.azure.ai.agents.ResponsesClient;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class InstantModelsApp {
    private InstantModelsApp() {
    }

    public static void main(String[] args) {
        ResponsesClient responsesClient = new AgentsClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .endpoint(InstantModelsConfig.projectEndpoint())
                .buildResponsesClient();

        ResponseCreateParams responseRequest = new ResponseCreateParams.Builder()
                .input(InstantModelsConfig.prompt())
                .model(InstantModelsConfig.model())
                .build();

        Response response = responsesClient.getResponseService().create(responseRequest);

        System.out.println("Model: " + InstantModelsConfig.model());
        System.out.println("Response: " + outputText(response));
        printUsage(response);
    }

    private static void printUsage(Response response) {
        Optional<ResponseUsage> usage = response.usage();
        if (usage.isEmpty()) {
            System.out.println("Usage: not returned by the service");
            return;
        }

        ResponseUsage responseUsage = usage.get();
        System.out.printf("Usage: input=%d tokens, output=%d tokens, total=%d tokens%n",
                responseUsage.inputTokens(), responseUsage.outputTokens(), responseUsage.totalTokens());

        ModelPricing pricing = new RetailPricingClient().getPricing(
            InstantModelsConfig.model(),
            InstantModelsConfig.pricingMeterPrefix(),
            InstantModelsConfig.pricingRegion(),
            InstantModelsConfig.pricingCurrency(),
            InstantModelsConfig.pricingScope());
        PricingEstimate estimate = pricing.estimateCost(responseUsage);
        System.out.printf(Locale.ROOT, "Cache details: standard-input=%d tokens, cached-input=%d tokens, cache-hit-rate=%.2f%%%n",
            estimate.standardInputTokens(),
            estimate.cachedInputTokens(),
            cacheHitRate(estimate.standardInputTokens(), estimate.cachedInputTokens()));

        System.out.printf("Pricing: input=%s, cached-input=%s, output=%s (model=%s, meter-prefix=%s, region=%s, scope=%s, retrieved=%s)%n",
            pricing.inputMeter().displayRate(),
            pricing.cachedInputMeter().map(RetailPriceMeter::displayRate).orElse("not found"),
            pricing.outputMeter().displayRate(),
            pricing.selectedModel(),
            pricing.meterPrefix(),
            pricing.region(),
            InstantModelsConfig.pricingScope(),
            pricing.retrievedAt());
        System.out.printf("Pricing meters: input='%s', cached-input='%s', output='%s'%n",
            pricing.inputMeter().meterName(),
            pricing.cachedInputMeter().map(RetailPriceMeter::meterName).orElse("not found"),
            pricing.outputMeter().meterName());
        System.out.printf("Cost breakdown: standard-input=%s %s, cached-input=%s %s, output=%s %s%n",
            estimate.currencyCode(),
            formatCost(estimate.standardInputCost()),
            estimate.currencyCode(),
            formatCost(estimate.cachedInputCost()),
            estimate.currencyCode(),
            formatCost(estimate.outputCost()));
        System.out.println("Estimated cost: " + estimate.currencyCode() + " " + estimate.totalCost().toPlainString());
    }

    private static String formatCost(BigDecimal cost) {
        return cost.setScale(8, RoundingMode.HALF_UP).toPlainString();
    }

    private static double cacheHitRate(long standardInputTokens, long cachedInputTokens) {
        long totalInputTokens = standardInputTokens + cachedInputTokens;
        if (totalInputTokens == 0) {
            return 0.0;
        }

        return cachedInputTokens * 100.0 / totalInputTokens;
    }

    private static String outputText(Response response) {
        String text = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining(System.lineSeparator()));

        return text.isBlank() ? response.output().toString() : text;
    }
}