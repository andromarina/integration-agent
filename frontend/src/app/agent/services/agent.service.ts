import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { AgentRequest, AgentResponse } from '../models/agent.model';

/**
 * Calls POST /agent/run. Streaming (SSE/WebSocket) may be added later without changing this contract.
 */
@Injectable({ providedIn: 'root' })
export class AgentService {
  private readonly http = inject(HttpClient);

  runAgent(request: AgentRequest): Observable<AgentResponse> {
    const base = environment.apiBaseUrl.replace(/\/$/, '');
    const url = `${base}/agent/run`;
    return this.http.post<AgentResponse>(url, request);
  }
}
