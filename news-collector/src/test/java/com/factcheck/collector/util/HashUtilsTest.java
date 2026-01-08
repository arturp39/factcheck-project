package com.factcheck.collector.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilsTest {

    @Test
    void sha256HexMatchesKnownValueForEmptyString() {
        assertThat(HashUtils.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256HexReturnsLowercase64CharHex() {
        String hash = HashUtils.sha256Hex("abc");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[0-9a-f]{64}$");
    }

    @Test
    void sha256HexIsDeterministic() {
        String h1 = HashUtils.sha256Hex("same");
        String h2 = HashUtils.sha256Hex("same");

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void sha256HexDiffersForDifferentInputs() {
        String h1 = HashUtils.sha256Hex("a");
        String h2 = HashUtils.sha256Hex("b");

        assertThat(h1).isNotEqualTo(h2);
    }
}