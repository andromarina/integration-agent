import type { AgentArtifact, AgentStep } from '../models/agent.model';

const CODEGEN_ACTION = 'generate_client_code';
const HTTP_ACTION = 'HttpCallTool';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * Prefer server `artifacts`; if missing or empty, derive from steps (older backends).
 */
export function normalizeResponseArtifacts(
  steps: AgentStep[],
  serverArtifacts: AgentArtifact[] | undefined | null,
): AgentArtifact[] {
  if (serverArtifacts && serverArtifacts.length > 0) {
    return serverArtifacts;
  }
  return deriveArtifactsFromSteps(steps);
}

function deriveArtifactsFromSteps(steps: AgentStep[]): AgentArtifact[] {
  const list: AgentArtifact[] = [];
  for (const step of steps) {
    if (step.action === CODEGEN_ACTION && step.status === 'SUCCESS' && isRecord(step.output)) {
      const output = step.output;
      if (output['success'] === true) {
        const data: Record<string, unknown> = {
          language: output['language'],
          code: output['code'],
          metadata: output['metadata'],
        };
        if (output['testCode'] !== undefined) {
          data['testCode'] = output['testCode'];
        }
        if (output['responseClassCode'] !== undefined) {
          data['responseClassCode'] = output['responseClassCode'];
        }
        list.push({
          kind: 'generated_client_code',
          stepNumber: step.stepNumber,
          data,
        });
      }
    }
    if (step.action === HTTP_ACTION) {
      const data: Record<string, unknown> = {};
      if (isRecord(step.input)) {
        data['method'] = step.input['method'];
        data['url'] = step.input['url'];
      }
      if (isRecord(step.output)) {
        data['statusCode'] = step.output['statusCode'];
        data['success'] = step.output['success'];
        data['body'] = step.output['body'];
      }
      list.push({ kind: 'http_call', stepNumber: step.stepNumber, data });
    }
  }
  return list;
}
