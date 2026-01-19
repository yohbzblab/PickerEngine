package org.engine.pickerengine.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstagramHttpBackoffPolicyTest {

    @Test
    void retryAfterHeaderOverridesBackoff() {
        long delayMs = InstagramHttpBackoffPolicy.computeDelayMs("5", 2, 1000, 8000);
        assertEquals(5000, delayMs);
    }
}
