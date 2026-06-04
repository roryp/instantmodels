# Token Efficiency Is a Product Feature

## A Java demo with Microsoft Foundry instant models

Live app: [http://aka.ms/costs](http://aka.ms/costs)

![Instant Models Lab dashboard](instant-models-dashboard.png){ width=6.5in }

*The dashboard starts with three focused workflows: a small instant model call, a prompt cache comparison, and a compaction demo.*

## The Main Idea

Token efficiency is not about making prompts tiny at all costs. It is about sending the smallest useful prompt, model, and tool surface for the job in front of you.

In this demo, every workflow answers the same practical question: what did we ask the model to read, what did it produce, and what did that cost?

The app keeps these signals on screen:

- Input tokens
- Output tokens
- Cached input tokens
- Cache hit rate
- Live retail price meters
- Estimated cost per call
- Tokens saved by compaction

That turns cost from an afterthought into a design signal.

## What The Demo Shows

The demo has three modes.

**1. Instant Demo**

A single focused prompt goes straight to a Foundry instant model through the Responses API. There is no agent orchestration and no extra tool schema attached to the request. For a simple one-shot answer, that keeps the input footprint small.

**2. Prompt Cache Demo**

The app sends the same long reference prompt twice with a stable `promptCacheKey`. The first call pays for the full prefix. The repeated call can reuse the cached prefix, shifting most input tokens into the cached-input meter.

**3. Compaction Demo**

The app takes long working notes and asks the model to produce a shorter durable summary for the next assistant turn. The point is honest accounting: compaction itself costs tokens, but the compacted summary can save future prompts from repeatedly carrying the whole transcript.

![Dashboard populated with representative result metrics](instant-models-results.png){ width=6.5in }

*Representative metric view using sample values from the README and article capture data. Live values vary by model, region, prompt, quota, and pricing response.*

## Code Example: A Direct Instant Model Call

The fast path uses the Responses API directly. That matters because agent frameworks are valuable when you need planning, tools, state, or multi-step behavior, but they are not free. For a simple prompt, direct model access keeps the request surface small.

```java
Response response = responsesClient().getResponseService().create(new ResponseCreateParams.Builder()
        .input(prompt)
        .model(InstantModelsConfig.model())
        .build());
```

The client is built with Microsoft Entra authentication through `DefaultAzureCredential`, which keeps API keys out of the sample.

```java
private ResponsesClient responsesClient() {
    return new AgentsClientBuilder()
            .credential(new DefaultAzureCredentialBuilder().build())
            .endpoint(InstantModelsConfig.projectEndpoint())
            .buildResponsesClient();
}
```

That is the kind of default I like in demos: fewer secrets, fewer moving parts, and a shorter path from idea to measurement.

## Code Example: Prompt Caching

Prompt caching is useful when a large part of the prompt is stable across calls. In this demo, the cache workflow creates a fresh key for each run, sends a long reusable reference prompt, and repeats it.

```java
private Response createCachedResponse(String prompt, String cacheKey) {
    ResponseCreateParams responseRequest = new ResponseCreateParams.Builder()
            .input(prompt)
            .model(InstantModelsConfig.model())
            .maxOutputTokens(80)
            .promptCacheKey(cacheKey)
            .build();

    return responsesClient().getResponseService().create(responseRequest);
}
```

The result is easy to explain to a team: if your app keeps sending the same policy document, catalog, rubric, or reference context, prompt caching can turn repeated context into a measurable optimization.

![Prompt cache result panel](prompt-cache-results.png){ width=6.2in }

*The repeated call keeps the same total input size, but most of the reusable prefix is reported as cached input.*

## Code Example: Live Cost Estimation

A useful token dashboard should not stop at token counts. This sample looks up current Azure Retail Prices API meters at runtime and combines those meters with service-reported usage.

```java
BigDecimal standardInputCost = inputMeter.costForTokens(standardInputTokens);
BigDecimal cachedInputCost = cachedInputMeter
        .map(meter -> meter.costForTokens(cachedInputTokens))
        .orElseGet(() -> inputMeter.costForTokens(cachedInputTokens));
BigDecimal outputCost = outputMeter.costForTokens(usage.outputTokens());
BigDecimal totalCost = standardInputCost.add(cachedInputCost).add(outputCost)
        .setScale(8, RoundingMode.HALF_UP);
```

The cost formula is intentionally transparent:

```text
cost = standard_input_tokens * standard_input_rate
     + cached_input_tokens * cached_input_rate
     + output_tokens * output_rate
```

The sample maps `gpt-chat-latest` to the `5.5 ShortCo` retail meter family by default, while still allowing a meter-prefix override through environment configuration.

## Code Example: Compaction As Conversation Hygiene

Long assistant conversations become expensive when every turn drags old exploration, logs, and resolved decisions back into the next model call. The compaction demo turns that into a visible tradeoff.

```java
Response response = responsesClient().getResponseService().create(new ResponseCreateParams.Builder()
        .input(prompt)
        .instructions(COMPACTION_INSTRUCTIONS)
        .model(InstantModelsConfig.model())
        .maxOutputTokens(360)
        .build());
```

Then the app compares the service-reported source input tokens with the compacted output tokens.

```java
static long tokensSaved(long sourceTokens, long compactedTokens) {
    return Math.max(sourceTokens - compactedTokens, 0);
}

static double tokenReductionRate(long sourceTokens, long compactedTokens) {
    if (sourceTokens == 0) {
        return 0.0;
    }

    return tokensSaved(sourceTokens, compactedTokens) * 100.0 / sourceTokens;
}
```

![Compaction result panel](compaction-results.png){ width=6.2in }

*Compaction is not free. The win comes when the shorter summary replaces repeated raw context in later turns.*

## Why This Matters

The easiest way to waste tokens is to make every request carry everything: the whole chat history, every tool definition, every schema, every old log, every unresolved thought. That can be useful during exploration, but it should not become the default shape of production calls.

This demo makes a better habit visible:

- Use direct model calls for simple one-shot work.
- Use the smallest model that can complete the task.
- Attach tools only when the model actually needs them.
- Reuse stable long context with prompt caching.
- Compact or restart long conversations after durable facts are captured.
- Watch output tokens too, because short prompts can still produce expensive answers.
- Price against live meters instead of stale spreadsheet assumptions.

Token efficiency is not just cost control. It improves latency, makes behavior easier to reason about, reduces accidental context leakage, and gives teams a shared language for AI application design.

## Try The Demo

From a clean checkout:

```powershell
mvn test
mvn compile exec:java
```

To run the web dashboard locally:

```powershell
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

For Azure deployment, the repo uses `azd` with Azure Container Apps, a user-assigned managed identity, Azure Container Registry, and Microsoft Foundry access through RBAC.

```powershell
az login
azd auth login
azd env new instantmodels --location westus3
azd up
```

## My Takeaway

The best AI engineering demos do not only prove that a model can answer. They show how the application thinks about context, cost, and operational tradeoffs.

That is what I like about this sample: token efficiency is visible in the product surface. You can see the input, the output, the cached portion, the live price meters, and the estimated cost. You can compare a tiny prompt with a cached long prompt. You can see compaction as a practical engineering choice instead of a vague best practice.

In a world where every extra token can become latency, cost, and complexity, that visibility is the feature.

Live app: [http://aka.ms/costs](http://aka.ms/costs)