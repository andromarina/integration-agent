package org.integration.agent.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmServiceTest {

    /**
     * Petstore lists many GETs; first in list is often {@code /pet/findByStatus} — goals that ask for a pet id should
     * still prefer {@code /pet/{petId}}.
     */
    @Test
    void pickBestGet_prefersPathTemplateWhenGoalMentionsPetById() {
        List<Map<String, String>> petstoreLikeOrder = List.of(
                Map.of("path", "/pet/findByStatus", "method", "GET", "description", "Finds Pets by status"),
                Map.of("path", "/pet/{petId}", "method", "GET", "description", "Find pet by ID")
        );
        Map<String, String> chosen = MockLlmService.pickBestGet(
                "call the petstore api: get pet by id 1.".toLowerCase(java.util.Locale.ROOT),
                petstoreLikeOrder
        );
        assertThat(chosen.get("path")).isEqualTo("/pet/{petId}");
    }

    @Test
    void pickBestGet_keepsFindByStatusWhenGoalMentionsStatus() {
        List<Map<String, String>> petstoreLikeOrder = List.of(
                Map.of("path", "/pet/findByStatus", "method", "GET", "description", "Finds Pets by status"),
                Map.of("path", "/pet/{petId}", "method", "GET", "description", "Find pet by ID")
        );
        Map<String, String> chosen = MockLlmService.pickBestGet(
                "list pets with status available".toLowerCase(java.util.Locale.ROOT),
                petstoreLikeOrder
        );
        assertThat(chosen.get("path")).isEqualTo("/pet/findByStatus");
    }
}
