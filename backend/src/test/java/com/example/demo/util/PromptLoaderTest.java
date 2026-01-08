package com.example.demo.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PromptLoaderTest {

    private final PromptLoader loader = new PromptLoader();

    @Test
    void loadPrompt_throwsIfFileNotFound() {
        assertThatThrownBy(() -> loader.loadPrompt("non-existent-name"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Prompt file not found");
    }
}
