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
import java.util.UUID;
import java.util.stream.Collectors;

public final class PromptCacheDemoApp {
    private static final String CACHE_KEY_PREFIX = "im-cache";

    private PromptCacheDemoApp() {
    }

    public static void main(String[] args) {
        ResponsesClient responsesClient = new AgentsClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .endpoint(InstantModelsConfig.projectEndpoint())
                .buildResponsesClient();

        ModelPricing pricing = new RetailPricingClient().getPricing(
                InstantModelsConfig.model(),
                InstantModelsConfig.pricingMeterPrefix(),
                InstantModelsConfig.pricingRegion(),
                InstantModelsConfig.pricingCurrency(),
                InstantModelsConfig.pricingScope());

        String runId = UUID.randomUUID().toString();
        String cacheKey = CACHE_KEY_PREFIX + "-" + runId.replace("-", "");
        String prompt = cacheablePrompt(runId);

        System.out.println("Prompt cache demo");
        System.out.println("Model: " + InstantModelsConfig.model());
        System.out.println("Cache key: " + cacheKey);
        System.out.println("Prompt characters: " + prompt.length());
        System.out.println();

        Response warmUp = createResponse(responsesClient, prompt, cacheKey);
        printResponseSummary("Warm-up call", warmUp, pricing);

        Response repeated = createResponse(responsesClient, prompt, cacheKey);
        printResponseSummary("Repeated call", repeated, pricing);
    }

    private static Response createResponse(ResponsesClient responsesClient, String prompt, String cacheKey) {
        ResponseCreateParams responseRequest = new ResponseCreateParams.Builder()
                .input(prompt)
                .model(InstantModelsConfig.model())
                .maxOutputTokens(80)
                .promptCacheKey(cacheKey)
                .build();

        return responsesClient.getResponseService().create(responseRequest);
    }

    private static void printResponseSummary(String label, Response response, ModelPricing pricing) {
        System.out.println(label);
        System.out.println("Response: " + outputText(response));

        Optional<ResponseUsage> usage = response.usage();
        if (usage.isEmpty()) {
            System.out.println("Usage: not returned by the service");
            System.out.println();
            return;
        }

        ResponseUsage responseUsage = usage.get();
        PricingEstimate estimate = pricing.estimateCost(responseUsage);
        BigDecimal uncachedCost = estimateUncachedCost(responseUsage, pricing);
        BigDecimal cacheSavings = uncachedCost.subtract(estimate.totalCost()).max(BigDecimal.ZERO);

        System.out.printf("Usage: input=%d tokens, output=%d tokens, total=%d tokens%n",
                responseUsage.inputTokens(), responseUsage.outputTokens(), responseUsage.totalTokens());
        System.out.printf(Locale.ROOT,
                "Cache details: standard-input=%d tokens, cached-input=%d tokens, cache-hit-rate=%.2f%%%n",
                estimate.standardInputTokens(),
                estimate.cachedInputTokens(),
                cacheHitRate(estimate.standardInputTokens(), estimate.cachedInputTokens()));
        System.out.printf("Cost breakdown: standard-input=%s %s, cached-input=%s %s, output=%s %s%n",
                estimate.currencyCode(),
                formatCost(estimate.standardInputCost()),
                estimate.currencyCode(),
                formatCost(estimate.cachedInputCost()),
                estimate.currencyCode(),
                formatCost(estimate.outputCost()));
        System.out.println("Estimated cost: " + estimate.currencyCode() + " " + estimate.totalCost().toPlainString());
        System.out.println("Estimated cache savings versus uncached input: "
                + estimate.currencyCode() + " " + formatCost(cacheSavings));
        System.out.println();
    }

    private static BigDecimal estimateUncachedCost(ResponseUsage usage, ModelPricing pricing) {
        return pricing.inputMeter().costForTokens(usage.inputTokens())
                .add(pricing.outputMeter().costForTokens(usage.outputTokens()))
                .setScale(8, RoundingMode.HALF_UP);
    }

    private static String cacheablePrompt(String runId) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are evaluating a reusable internal reference document about instant models. ")
                .append("Use only the reference material below, then answer the final question briefly.\n\n")
                .append("Demo run id: ").append(runId).append("\n\n")
                .append("Reference material:\n");

        for (int index = 1; index <= 120; index++) {
            builder.append("Section ").append(index).append(": ")
                    .append("Instant models let developers call supported models by name without creating deployments. ")
                    .append("They are useful for prototyping, model comparison, early workflow testing, and cost exploration. ")
                    .append("Applications should still consider deployments when they need reserved throughput, custom controls, ")
                    .append("data residency choices, or production isolation. ")
                    .append("Prompt caching is useful when a large stable prefix is reused across calls because cached input ")
                    .append("tokens can be billed differently from standard input tokens.\n");
        }

        builder.append("\nFinal question: In one sentence, why is prompt caching useful for this instant models demo?");
        return builder.toString();
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
}