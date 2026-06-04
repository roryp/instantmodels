package com.example.instantmodels;

import com.azure.ai.agents.AgentsClientBuilder;
import com.azure.ai.agents.ResponsesClient;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
class DemoRunService {
    private static final String CACHE_KEY_PREFIX = "im-web-cache";
    private static final String DEFAULT_COMPACT_PROMPT = String.join("\n",
            "Goal: ship a small Java dashboard change that explains instant model throughput limits.",
            "Context gathered: Microsoft Foundry instant models are preview-scoped to West US 3 and can be called by model name without creating a deployment. They draw from a per-model global quota pool assigned to the subscription. The app should keep token usage, pricing, and cache behavior visible because this repository is a cost-transparency sample.",
            "Online docs checked: instant models are for getting started, prototyping, and trying new models. Deployments remain the right choice for reserved throughput, custom guardrails, data residency requirements, endpoint-specific configuration, fine-grained quota partitioning, fine-tuned models, or predictable production capacity.",
            "Sanitized validation finding: one development subscription reported Tier 5 Global Standard quota for gpt-chat-latest of 50,000 requests per minute and 5,000,000 tokens per minute, with no Global Standard deployment quota reserved at the time of the check. Do not include subscription IDs, tenant IDs, user names, or generated Azure environment files in docs.",
            "Implementation notes: update README in the Example overview and Token Efficiency sections, keep dashboard controls compact, run mvn test after Java changes, and use azd up for full deployment validation rather than manual container app commands.",
            "Open question: after the docs update, add a dashboard example that compacts long working notes into a shorter durable summary and shows the token savings for future prompts.");
    private static final String COMPACTION_INSTRUCTIONS = String.join(" ",
            "Compact the submitted working notes into a concise durable context summary for a future assistant turn.",
            "Use at most six compact sentences, no markdown bullets, and no nested lists.",
            "Preserve only next-turn essentials: goal, key facts, docs or files to update, validation and deploy commands, blockers, and privacy constraints.",
            "Remove repetition, resolved dead ends, greetings, transient logs, and exact values that are not needed for the next step.",
            "Summarize quota findings as sanitized quota evidence instead of repeating every number.",
            "Omit optional follow-ups unless they are required for the next action.",
            "Return only the compacted summary.");

    InstantDemoResult runInstantDemo(String promptOverride) {
        String prompt = InstantModelsConfig.valueOrDefault(promptOverride, InstantModelsConfig.prompt());
        Response response = responsesClient().getResponseService().create(new ResponseCreateParams.Builder()
                .input(prompt)
                .model(InstantModelsConfig.model())
                .build());

        ModelPricing pricing = pricing();
        CallSummary summary = summarize("Instant model call", response, pricing);
        return new InstantDemoResult(
                InstantModelsConfig.model(),
                prompt,
                outputText(response),
                summary.usage(),
                summary.cache(),
                summary.cost(),
                pricingSummary(pricing));
    }

    PromptCacheDemoResult runPromptCacheDemo() {
        ModelPricing pricing = pricing();
        String runId = UUID.randomUUID().toString();
        String cacheKey = CACHE_KEY_PREFIX + "-" + runId.replace("-", "");
        String prompt = cacheablePrompt(runId);

        Response warmUp = createCachedResponse(prompt, cacheKey);
        Response repeated = createCachedResponse(prompt, cacheKey);

        return new PromptCacheDemoResult(
                InstantModelsConfig.model(),
                cacheKey,
                prompt.length(),
                pricingSummary(pricing),
                List.of(
                        summarize("Warm-up call", warmUp, pricing),
                        summarize("Repeated call", repeated, pricing)));
    }

    CompactDemoResult runCompactDemo(String promptOverride) {
        String prompt = InstantModelsConfig.valueOrDefault(promptOverride, DEFAULT_COMPACT_PROMPT);
        Response response = responsesClient().getResponseService().create(new ResponseCreateParams.Builder()
                .input(prompt)
                .instructions(COMPACTION_INSTRUCTIONS)
                .model(InstantModelsConfig.model())
                .maxOutputTokens(360)
                .build());

        ResponseUsage usage = usage(response);
        ModelPricing pricing = pricing();
        PricingEstimate estimate = pricing.estimateCost(usage);
        String compactedPrompt = outputText(response);
        long compactedTokens = usage.outputTokens();

        return new CompactDemoResult(
                InstantModelsConfig.model(),
                prompt,
                compactedPrompt,
                prompt.length(),
                compactedPrompt.length(),
                new UsageSummary(usage.inputTokens(), usage.outputTokens(), usage.totalTokens()),
                new CompactionSummary(
                        usage.inputTokens(),
                        compactedTokens,
                        tokensSaved(usage.inputTokens(), compactedTokens),
                        tokenReductionRate(usage.inputTokens(), compactedTokens)),
                new CostSummary(
                        estimate.currencyCode(),
                        formatCost(estimate.standardInputCost()),
                        formatCost(estimate.cachedInputCost()),
                        formatCost(estimate.outputCost()),
                        estimate.totalCost().toPlainString(),
                        formatCost(BigDecimal.ZERO)),
                pricingSummary(pricing));
    }

    private Response createCachedResponse(String prompt, String cacheKey) {
        ResponseCreateParams responseRequest = new ResponseCreateParams.Builder()
                .input(prompt)
                .model(InstantModelsConfig.model())
                .maxOutputTokens(80)
                .promptCacheKey(cacheKey)
                .build();

        return responsesClient().getResponseService().create(responseRequest);
    }

    private ResponsesClient responsesClient() {
        return new AgentsClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .endpoint(InstantModelsConfig.projectEndpoint())
                .buildResponsesClient();
    }

    private ModelPricing pricing() {
        return new RetailPricingClient().getPricing(
                InstantModelsConfig.model(),
                InstantModelsConfig.pricingMeterPrefix(),
                InstantModelsConfig.pricingRegion(),
                InstantModelsConfig.pricingCurrency(),
                InstantModelsConfig.pricingScope());
    }

    private CallSummary summarize(String label, Response response, ModelPricing pricing) {
        ResponseUsage usage = usage(response);
        PricingEstimate estimate = pricing.estimateCost(usage);
        BigDecimal uncachedCost = pricing.inputMeter().costForTokens(usage.inputTokens())
                .add(pricing.outputMeter().costForTokens(usage.outputTokens()))
                .setScale(8, RoundingMode.HALF_UP);
        BigDecimal cacheSavings = uncachedCost.subtract(estimate.totalCost()).max(BigDecimal.ZERO);

        return new CallSummary(
                label,
                outputText(response),
                new UsageSummary(usage.inputTokens(), usage.outputTokens(), usage.totalTokens()),
                new CacheSummary(
                        estimate.standardInputTokens(),
                        estimate.cachedInputTokens(),
                        cacheHitRate(estimate.standardInputTokens(), estimate.cachedInputTokens())),
                new CostSummary(
                        estimate.currencyCode(),
                        formatCost(estimate.standardInputCost()),
                        formatCost(estimate.cachedInputCost()),
                        formatCost(estimate.outputCost()),
                        estimate.totalCost().toPlainString(),
                        formatCost(cacheSavings)));
    }

    private PricingSummary pricingSummary(ModelPricing pricing) {
        return new PricingSummary(
                pricing.selectedModel(),
                pricing.meterPrefix(),
                pricing.region(),
                InstantModelsConfig.pricingScope(),
                pricing.retrievedAt().toString(),
                pricing.inputMeter().displayRate(),
                pricing.cachedInputMeter().map(RetailPriceMeter::displayRate).orElse("not found"),
                pricing.outputMeter().displayRate(),
                pricing.inputMeter().meterName(),
                pricing.cachedInputMeter().map(RetailPriceMeter::meterName).orElse("not found"),
                pricing.outputMeter().meterName());
    }

        private static ResponseUsage usage(Response response) {
                return response.usage()
                                .orElseThrow(() -> new IllegalStateException("Usage was not returned by the service."));
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

        static long tokensSaved(long sourceTokens, long compactedTokens) {
                return Math.max(sourceTokens - compactedTokens, 0);
        }

        static double tokenReductionRate(long sourceTokens, long compactedTokens) {
                if (sourceTokens == 0) {
                        return 0.0;
                }

                return tokensSaved(sourceTokens, compactedTokens) * 100.0 / sourceTokens;
        }

    record InstantDemoResult(
            String model,
            String prompt,
            String response,
            UsageSummary usage,
            CacheSummary cache,
            CostSummary cost,
            PricingSummary pricing) {
    }

    record PromptCacheDemoResult(
            String model,
            String cacheKey,
            int promptCharacters,
            PricingSummary pricing,
            List<CallSummary> calls) {
    }

    record CompactDemoResult(
            String model,
            String prompt,
            String compactedPrompt,
            int sourceCharacters,
            int compactedCharacters,
            UsageSummary usage,
            CompactionSummary compaction,
            CostSummary cost,
            PricingSummary pricing) {
    }

    record CallSummary(
            String label,
            String response,
            UsageSummary usage,
            CacheSummary cache,
            CostSummary cost) {
    }

    record UsageSummary(long inputTokens, long outputTokens, long totalTokens) {
    }

    record CacheSummary(long standardInputTokens, long cachedInputTokens, double cacheHitRate) {
    }

        record CompactionSummary(long sourceTokens, long compactedTokens, long tokensSaved, double tokenReductionRate) {
        }

    record CostSummary(
            String currencyCode,
            String standardInput,
            String cachedInput,
            String output,
            String total,
            String cacheSavings) {
    }

    record PricingSummary(
            String model,
            String meterPrefix,
            String region,
            String scope,
            String retrievedAt,
            String inputRate,
            String cachedInputRate,
            String outputRate,
            String inputMeter,
            String cachedInputMeter,
            String outputMeter) {
    }
}