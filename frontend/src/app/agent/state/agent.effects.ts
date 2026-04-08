import { HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of } from 'rxjs';
import { AgentService } from '../services/agent.service';
import { agentActions } from './agent.actions';

function toErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (typeof error.error === 'string' && error.error.trim()) {
      return error.error;
    }
    if (error.error && typeof error.error === 'object' && 'message' in error.error) {
      const message = (error.error as { message: unknown }).message;
      if (typeof message === 'string') {
        return message;
      }
    }
    return error.message || `HTTP ${error.status}`;
  }
  if (error && typeof error === 'object' && 'message' in error && typeof (error as { message: unknown }).message === 'string') {
    return (error as { message: string }).message;
  }
  return 'Request failed';
}

@Injectable()
export class AgentEffects {
  private readonly actions$ = inject(Actions);
  private readonly agentService = inject(AgentService);

  runAgent$ = createEffect(() =>
    this.actions$.pipe(
      ofType(agentActions.runAgent),
      exhaustMap(({ request }) =>
        this.agentService.runAgent(request).pipe(
          map((response) => agentActions.runAgentSuccess({ response })),
          catchError((error: unknown) => of(agentActions.runAgentFailure({ error: toErrorMessage(error) })))
        )
      )
    )
  );
}
