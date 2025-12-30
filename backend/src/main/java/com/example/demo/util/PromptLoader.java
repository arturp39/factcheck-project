package com.example.demo.util;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class PromptLoader {

    public String loadPrompt() {
        return loadPrompt("factcheck");
    }

    /**
     * Load a named prompt from /prompts/{name}.txt on the classpath.
     */
    public String loadPrompt(String name) {
        String path = "/prompts/" + name + ".txt";
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Prompt file not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt file: " + path, e);
        }
    }
}