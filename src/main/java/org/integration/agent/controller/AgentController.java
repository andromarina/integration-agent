package org.integration.agent.controller;

import jakarta.validation.Valid;
import org.integration.agent.model.AgentRequest;
import org.integration.agent.model.AgentResponse;
import org.integration.agent.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    public ResponseEntity<AgentResponse> run(@Valid @RequestBody AgentRequest request) {
        AgentResponse response = agentService.run(request);
        return ResponseEntity.ok(response);
    }
}
