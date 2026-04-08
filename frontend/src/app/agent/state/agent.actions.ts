import { createActionGroup, props } from '@ngrx/store';
import type { AgentPanelTab, AgentRequest, AgentResponse } from '../models/agent.model';

export const agentActions = createActionGroup({
  source: 'Agent',
  events: {
    'Run Agent': props<{ request: AgentRequest }>(),
    'Run Agent Success': props<{ response: AgentResponse }>(),
    'Run Agent Failure': props<{ error: string }>(),
    'Set Panel Tab': props<{ tab: AgentPanelTab }>(),
  },
});
