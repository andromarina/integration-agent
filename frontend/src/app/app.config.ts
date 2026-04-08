import { ApplicationConfig, isDevMode, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState, provideStore } from '@ngrx/store';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { routes } from './app.routes';
import { AgentEffects } from './agent/state/agent.effects';
import { agentFeature } from './agent/state/agent.reducer';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(),
    provideAnimations(),
    provideRouter(routes),
    provideStore(),
    provideState(agentFeature),
    provideEffects(AgentEffects),
    ...(isDevMode()
      ? [
          provideStoreDevtools({
            maxAge: 25,
            trace: false,
          }),
        ]
      : []),
  ],
};
