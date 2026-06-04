package com.example.instantmodels;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DemoRunServiceTest {
    @Test
    void tokensSavedSubtractsCompactedTokens() {
        assertEquals(850, DemoRunService.tokensSaved(1_000, 150));
    }

    @Test
    void tokensSavedDoesNotGoNegative() {
        assertEquals(0, DemoRunService.tokensSaved(120, 240));
    }

    @Test
    void tokenReductionRateReturnsSavedShare() {
        assertEquals(85.0, DemoRunService.tokenReductionRate(1_000, 150), 0.001);
    }

    @Test
    void tokenReductionRateHandlesEmptySource() {
        assertEquals(0.0, DemoRunService.tokenReductionRate(0, 50), 0.001);
    }
}