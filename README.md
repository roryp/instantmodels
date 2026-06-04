# Instant Models Java Sample

Instant models in Microsoft Foundry let you call supported models by name without first creating a deployment. They are useful for prototyping, comparing models, trying new releases quickly, and building early application flows before you decide whether you need a dedicated deployment for reserved throughput, custom controls, or production isolation.

This Java sample calls an instant model from a Foundry project endpoint, prints token usage, and estimates cost per call with live pricing from the Azure Retail Prices API.

The sample follows the Microsoft Foundry Java quickstart pattern with `com.azure:azure-ai-agents:2.0.0` and uses Microsoft Entra authentication through `DefaultAzureCredential`. No API key or project-specific secret is stored in this repository.

## What It Does

- Calls a Microsoft Foundry project endpoint with the Responses API.
- Uses an instant model by name, so no model deployment is required.
- Loads local settings from `.env`, while keeping `.env` out of git.
- Prints the model response plus input, output, total, and cached-input token usage.
- Looks up current prices at runtime from `https://prices.azure.com/api/retail/prices`.
- Estimates per-call cost from the returned token usage and live retail pricing meters.

## Prerequisites

- Java 17 or newer
- Maven 3.9 or newer
- Azure CLI
- A Microsoft Foundry project in a region that supports instant models during preview
- A signed-in Azure user with the Foundry User role on the project or account

Sign in before running the sample:

```powershell
az login
```

## Configure

Create a local `.env` file from the checked-in template:

```powershell
Copy-Item .env.example .env
notepad .env
```

Set your Foundry project endpoint:

```text
FOUNDRY_PROJECT_ENDPOINT=https://<resource-name>.services.ai.azure.com/api/projects/<project-name>
```

The app reads real environment variables first, then falls back to `.env`. This lets CI/CD or shell variables override local development settings.

## Provision Azure Resources

This repo includes an Azure Developer CLI (`azd`) and Bicep setup that creates the resources needed for the sample:

- Resource group
- Azure AI Services account with Foundry project management enabled
- Microsoft Foundry project
- Azure AI User role assignment for the signed-in deployer on the Foundry project
- Azure Container Registry
- Azure Container Apps environment and Container App
- Managed identity with ACR pull and Foundry project access

The default location is `westus3`, which is the preview region used for instant model testing.

```powershell
az login
azd auth login
azd env new instantmodels --location westus3
azd up
```

`azd up` provisions the Azure resources, builds the Spring Boot Docker image locally, pushes it to Azure Container Registry, deploys it to Azure Container Apps, and prints the live endpoint. Local Docker must be running because this template intentionally disables ACR remote build.

For later code-only updates, run:

```powershell
azd deploy web
```

The service is mapped in [azure.yaml](azure.yaml) as a standard `containerapp` service with a local Docker build. The Bicep tags the Container App with `azd-service-name: web` so azd can deploy to it.

To remove the Azure resources later:

```powershell
azd down
```

## Configuration Reference

| Setting | Required | Default | Description |
| --- | --- | --- | --- |
| `FOUNDRY_PROJECT_ENDPOINT` | Yes | None | Foundry project endpoint, for example `https://<resource>.services.ai.azure.com/api/projects/<project>` |
| `FOUNDRY_MODEL` | No | `gpt-chat-latest` | Instant model name passed to the Responses API |
| `FOUNDRY_PROMPT` | No | A one-sentence instant models prompt | Prompt sent to the selected model |
| `FOUNDRY_PRICING_REGION` | No | `westus3` | Azure Retail Prices `armRegionName` used for pricing lookup |
| `FOUNDRY_PRICING_CURRENCY` | No | `USD` | Currency code passed to the Retail Prices API |
| `FOUNDRY_PRICING_SCOPE` | No | `Gl` | Retail meter scope. `Gl` is global; `Dz` is data zone |
| `FOUNDRY_PRICING_METER_PREFIX` | No | Model-derived | Override when a model alias maps to a specific retail meter family |

For `gpt-chat-latest`, this sample maps the model alias to the `5.5 ShortCo` retail meter prefix. If your selected model maps differently, set `FOUNDRY_PRICING_METER_PREFIX` in `.env`.

## Run

From a clean checkout, run:

```powershell
mvn test
mvn compile exec:java
```

To run the Spring Boot web dashboard locally:

```powershell
mvn spring-boot:run
```

Open `http://localhost:8080` and use the buttons to run the instant model demo or the prompt cache demo.

Use `mvn compile exec:java` after `mvn clean` or from a fresh clone. `mvn exec:java` by itself only works after classes already exist under `target/classes`.

Optional one-off overrides:

```powershell
$env:FOUNDRY_MODEL = "gpt-chat-latest"
$env:FOUNDRY_PROMPT = "Explain instant models in one sentence."
mvn compile exec:java
```

## Prompt Cache Demo

The default sample is intentionally small, so it usually has no cached input tokens. For demos, use the dedicated prompt-cache example. It sends the same long prompt twice with a stable `promptCacheKey`, then compares the warm-up call with the repeated call. This demo intentionally uses a much longer prompt than the default sample so the cache behavior is visible.

```powershell
mvn compile exec:java '-Dexec.mainClass=com.example.instantmodels.PromptCacheDemoApp'
```

The repeated call should show cached input tokens when the service reuses the long prompt prefix:

```text
Prompt cache demo
Model: gpt-chat-latest
Cache key: im-cache-<run-id>
Prompt characters: 60935

Warm-up call
Usage: input=10045 tokens, output=33 tokens, total=10078 tokens
Cache details: standard-input=10045 tokens, cached-input=0 tokens, cache-hit-rate=0.00%
Estimated cost: USD 0.05121500
Estimated cache savings versus uncached input: USD 0.00000000

Repeated call
Usage: input=10045 tokens, output=34 tokens, total=10079 tokens
Cache details: standard-input=317 tokens, cached-input=9728 tokens, cache-hit-rate=96.84%
Estimated cost: USD 0.00746900
Estimated cache savings versus uncached input: USD 0.04377600
```

## Example Output

The exact response and token counts vary by run, but the output looks like this:

```text
Model: gpt-chat-latest
Response: Instant models in Microsoft Foundry are preconfigured AI models designed for immediate use with low-latency inference.
Usage: input=19 tokens, output=106 tokens, total=125 tokens
Cache details: standard-input=19 tokens, cached-input=0 tokens, cache-hit-rate=0.00%
Pricing: input=USD 5 per 1M tokens, cached-input=USD 0.5 per 1M tokens, output=USD 30 per 1M tokens (model=gpt-chat-latest, meter-prefix=5.5 ShortCo, region=westus3, scope=Gl, retrieved=2026-06-04T07:58:17Z)
Pricing meters: input='5.5 ShortCo inp Gl 1M Tokens', cached-input='5.5 ShortCo cd inp Gl 1M Tokens', output='5.5 ShortCo opt Gl 1M Tokens'
Cost breakdown: standard-input=USD 0.00009500, cached-input=USD 0.00000000, output=USD 0.00318000
Estimated cost: USD 0.00327500
```

## Cost Calculation

The Responses API returns usage metadata. The sample uses those token counts and live retail pricing meters to estimate cost.

Base formula:

```text
cost = (input_tokens / 1,000,000 * input_price_per_1M_tokens)
	+ (output_tokens / 1,000,000 * output_price_per_1M_tokens)
```

If the response reports cached input tokens and the retail catalog has a cached-input meter, cached tokens use the cached-input rate:

```text
cost = (standard_input_tokens / 1,000,000 * input_price_per_1M_tokens)
	+ (cached_input_tokens / 1,000,000 * cached_input_price_per_1M_tokens)
	+ (output_tokens / 1,000,000 * output_price_per_1M_tokens)
```

The Azure Retail Prices API is queried at runtime, so the estimate reflects the current public retail catalog response for the configured model meter prefix, region, currency, and scope. Your actual bill can still differ if your subscription has discounts, commitments, credits, or private pricing.

## Project Layout

```text
.
|-- .env.example
|-- .gitignore
|-- pom.xml
|-- README.md
`-- src
    |-- main
    |   |-- java/com/example/instantmodels
    |   |   |-- DemoController.java
    |   |   |-- DemoRunService.java
    |   |   |-- InstantModelsApp.java
    |   |   |-- InstantModelsConfig.java
    |   |   |-- InstantModelsWebApplication.java
    |   |   |-- ModelPricing.java
    |   |   |-- PromptCacheDemoApp.java
    |   |   `-- RetailPricingClient.java
    |   |-- resources/application.properties
    |   `-- resources/static
    `-- test/java/com/example/instantmodels/InstantModelsConfigTest.java
```

## Troubleshooting

### `ClassNotFoundException: InstantModelsApp`

Run the compile phase first:

```powershell
mvn compile exec:java
```

This happens after `mvn clean` because `mvn exec:java` alone does not compile classes.

### `Set FOUNDRY_PROJECT_ENDPOINT`

Create `.env` from `.env.example` and set `FOUNDRY_PROJECT_ENDPOINT`, or set it as a real environment variable before running Maven.

### Authentication Errors

Run `az login`, then confirm the signed-in user has the Foundry User role on the Foundry project or account.

### Pricing Meter Not Found

The selected model might be an alias or might map to a different retail meter family. Set `FOUNDRY_PRICING_METER_PREFIX` to the matching Azure Retail Prices meter prefix, then rerun.

### Model Not Available

Instant models are preview features and availability can vary by region, subscription, and quota. Set `FOUNDRY_MODEL` to another instant-capable model shown in your Foundry model catalog.

## GitHub Safety

The local `.env` file is intentionally ignored by [.gitignore](.gitignore). Commit [.env.example](.env.example), not `.env`.

Before pushing, a good final check is:

```powershell
mvn test
mvn clean
```

This repository should not contain API keys, project endpoints, access tokens, Maven build output, IDE folders, or log files.

## Notes

- During preview, instant models require a supported Foundry project region. The original test project used West US 3.
- The sample currently defaults to `gpt-chat-latest` because it was available in the tested subscription.
- The pricing lookup is unauthenticated and uses public retail prices, not negotiated enterprise rates.