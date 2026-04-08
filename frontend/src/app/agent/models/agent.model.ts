export type StepStatus = 'SUCCESS' | 'FAILED';

export type AgentArtifactKind = 'generated_client_code' | 'generated_java_type' | 'http_call';

export interface AgentStep {
  stepNumber: number;
  action: string;
  input: Record<string, unknown> | null;
  output: unknown;
  status: StepStatus;
  errorMessage?: string | null;
}

export interface AgentRequest {
  goal: string;
  openApiSpec: string;
}

export interface AgentArtifact {
  kind: AgentArtifactKind;
  stepNumber: number;
  data: Record<string, unknown>;
}

export interface AgentResponse {
  steps: AgentStep[];
  finalResult: unknown;
  artifacts?: AgentArtifact[];
}

export type AgentPanelTab = 'result' | 'code' | 'api';
