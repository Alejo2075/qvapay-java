package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class QvaPayVersionTest {

    @Test
    void versionIsResolvedFromFilteredResource() {
        assertNotEquals("unknown", QvaPayVersion.get());
        assertFalse(QvaPayVersion.get().contains("${"));
    }
}
