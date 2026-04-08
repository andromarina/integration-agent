import { createFeature, createReducer, on } from '@ngrx/store';
import type { AgentArtifact, AgentPanelTab, AgentRequest, AgentStep } from '../models/agent.model';
import { normalizeResponseArtifacts } from '../util/artifacts.mapper';
import { formatFinalResult } from '../util/final-result-format';
import { agentActions } from './agent.actions';

export interface AgentState {
  loading: boolean;
  steps: AgentStep[];
  result: string | null;
  error: string | null;
  lastRequest: AgentRequest | null;
  artifacts: AgentArtifact[];
  panelTab: AgentPanelTab;
}

const initialState: AgentState = {
  loading: false,
  steps: [],
  result: null,
  error: null,
  lastRequest: null,
  artifacts: [],
  panelTab: 'result',
};

function sortSteps(steps: AgentStep[]): AgentStep[] {
  return [...steps].sort((a, b) => a.stepNumber - b.stepNumber);
}

const reducer = createReducer(
  initialState,
  on(agentActions.runAgent, (state, { request }): AgentState => ({
    ...state,
    loading: true,
    error: null,
    steps: [],
    result: null,
    artifacts: [],
    panelTab: 'result',
    lastRequest: request,
  })),
  on(agentActions.runAgentSuccess, (state, { response }): AgentState => {
    const steps = sortSteps(response.steps ?? []);
    return {
      ...state,
      loading: false,
      steps,
      result: formatFinalResult(response.finalResult),
      error: null,
      artifacts: normalizeResponseArtifacts(steps, response.artifacts),
      panelTab: 'result',
    };
  }),
  on(agentActions.setPanelTab, (state, { tab }): AgentState => ({
    ...state,
    panelTab: tab,
  })),
  on(agentActions.runAgentFailure, (state, { error }): AgentState => ({
    ...state,
    loading: false,
    error,
  }))
);

export const agentFeature = createFeature({
  name: 'agent',
  reducer,
});
