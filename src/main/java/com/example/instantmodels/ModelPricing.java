package com.example.instantmodels;

import com.openai.models.responses.ResponseUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

record ModelPricing(
        String selectedModel,
        String meterPrefix,
        String region,
        String currencyCode,
        RetailPriceMeter inputMeter,
        Optional<RetailPriceMeter> cachedInputMeter,
        RetailPriceMeter outputMeter,
        Instant retrievedAt) {
    PricingEstimate estimateCost(ResponseUsage usage) {
        long cachedInputTokens = Math.min(cachedInputTokens(usage), usage.inputTokens());
        long standardInputTokens = usage.inputTokens() - cachedInputTokens;

        BigDecimal standardInputCost = inputMeter.costForTokens(standardInputTokens);
        BigDecimal cachedInputCost = cachedInputMeter
                .map(meter -> meter.costForTokens(cachedInputTokens))
                .orElseGet(() -> inputMeter.costForTokens(cachedInputTokens));
        BigDecimal outputCost = outputMeter.costForTokens(usage.outputTokens());
        BigDecimal totalCost = standardInputCost.add(cachedInputCost).add(outputCost)
                .setScale(8, RoundingMode.HALF_UP);

        return new PricingEstimate(standardInputTokens, cachedInputTokens, usage.outputTokens(),
                standardInputCost, cachedInputCost, outputCost, totalCost, currencyCode);
    }

    private static long cachedInputTokens(ResponseUsage usage) {
        try {
            return usage.inputTokensDetails().cachedTokens();
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}

record RetailPriceMeter(String meterName, BigDecimal pricePerMillionTokens, String currencyCode, String unitOfMeasure) {
    BigDecimal costForTokens(long tokens) {
        return pricePerMillionTokens
                .multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.HALF_UP);
    }

    String displayRate() {
        return currencyCode + " " + pricePerMillionTokens.stripTrailingZeros().toPlainString()
                + " per " + unitOfMeasure + " tokens";
    }
}

record PricingEstimate(
        long standardInputTokens,
        long cachedInputTokens,
        long outputTokens,
        BigDecimal standardInputCost,
        BigDecimal cachedInputCost,
        BigDecimal outputCost,
        BigDecimal totalCost,
        String currencyCode) {
}