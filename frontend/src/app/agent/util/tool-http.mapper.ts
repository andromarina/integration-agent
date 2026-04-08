import { AgentStep } from '../models/agent.model';

const HTTP_ACTION_HINTS = ['HttpCallTool', 'http', 'call_api'];

export interface ToolHttpSummary {
  stepNumber: number;
  method: string | null;
  endpoint: string | null;
  statusCode: number | null;
  success: boolean | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readStatusCode(output: unknown): number | null {
  if (!isRecord(output)) {
    return null;
  }
  const code = output['statusCode'];
  if (typeof code === 'number' && Number.isFinite(code)) {
    return code;
  }
  return null;
}

function readSuccess(output: unknown): boolean | null {
  if (!isRecord(output)) {
    return null;
  }
  const success = output['success'];
  if (typeof success === 'boolean') {
    return success;
  }
  return null;
}

/**
 * Best-effort extraction for HTTP tool steps (matches backend HttpCallTool payloads).
 */
export function extractHttpSummaries(steps: AgentStep[]): ToolHttpSummary[] {
  const result: ToolHttpSummary[] = [];
  for (const step of steps) {
    const actionLower = step.action.toLowerCase();
    const looksHttp =
      HTTP_ACTION_HINTS.some((hint) => step.action === hint || actionLower.includes(hint.toLowerCase())) ||
      (isRecord(step.input) && 'url' in step.input && 'method' in step.input);

    if (!looksHttp) {
      continue;
    }

    const input = isRecord(step.input) ? step.input : null;
    const method =
      input && typeof input['method'] === 'string' ? input['method'].trim().toUpperCase() : null;
    const endpoint = input && typeof input['url'] === 'string' ? input['url'] : null;

    result.push({
      stepNumber: step.stepNumber,
      method,
      endpoint,
      statusCode: readStatusCode(step.output),
      success: readSuccess(step.output),
    });
  }
  return result;
}
