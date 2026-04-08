import { CommonModule } from '@angular/common';
import { Component, ElementRef, computed, effect, inject, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Store } from '@ngrx/store';
import { toSignal } from '@angular/core/rxjs-interop';
import hljs from 'highlight.js/lib/core';
import java from 'highlight.js/lib/languages/java';
import { agentActions } from '../agent/state/agent.actions';
import { agentFeature } from '../agent/state/agent.reducer';
import type { AgentPanelTab, AgentStep } from '../agent/models/agent.model';
import { extractHttpSummaries } from '../agent/util/tool-http.mapper';
import { JsonFormatPipe } from '../pipes/json-format.pipe';

hljs.registerLanguage('java', java);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatToolbarModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatExpansionModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    JsonFormatPipe,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly store = inject(Store);
  private readonly sanitizer = inject(DomSanitizer);

  protected goalText = '';
  protected openApiText = '';

  protected readonly loading = toSignal(this.store.select(agentFeature.selectLoading), { initialValue: false });
  protected readonly steps = toSignal(this.store.select(agentFeature.selectSteps), { initialValue: [] as AgentStep[] });
  protected readonly result = toSignal(this.store.select(agentFeature.selectResult), { initialValue: null as string | null });
  protected readonly error = toSignal(this.store.select(agentFeature.selectError), { initialValue: null as string | null });
  protected readonly lastRequest = toSignal(this.store.select(agentFeature.selectLastRequest), { initialValue: null });
  protected readonly artifacts = toSignal(this.store.select(agentFeature.selectArtifacts), { initialValue: [] });
  protected readonly panelTab = toSignal(this.store.select(agentFeature.selectPanelTab), { initialValue: 'result' as AgentPanelTab });

  private readonly timelineEnd = viewChild<ElementRef<HTMLElement>>('timelineEnd');

  protected readonly codegenClientArtifacts = computed(() =>
    this.artifacts().filter((artifact) => artifact.kind === 'generated_client_code'),
  );

  protected readonly codegenTypeArtifacts = computed(() =>
    this.artifacts().filter((artifact) => artifact.kind === 'generated_java_type'),
  );

  protected readonly httpRows = computed(() => {
    const httpFromArtifacts = this.artifacts().filter((artifact) => artifact.kind === 'http_call');
    if (httpFromArtifacts.length > 0) {
      return httpFromArtifacts.map((artifact) => ({
        stepNumber: artifact.stepNumber,
        method:
          typeof artifact.data['method'] === 'string' ? artifact.data['method'].trim().toUpperCase() : null,
        endpoint: typeof artifact.data['url'] === 'string' ? artifact.data['url'] : null,
        statusCode: typeof artifact.data['statusCode'] === 'number' ? artifact.data['statusCode'] : null,
        success: typeof artifact.data['success'] === 'boolean' ? artifact.data['success'] : null,
      }));
    }
    return extractHttpSummaries(this.steps());
  });

  constructor() {
    effect(() => {
      const stepList = this.steps();
      if (stepList.length === 0) {
        return;
      }
      queueMicrotask(() => {
        const end = this.timelineEnd();
        end?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'end' });
      });
    });
  }

  protected panelTabIndex(): number {
    const map: Record<AgentPanelTab, number> = { result: 0, code: 1, api: 2 };
    return map[this.panelTab()] ?? 0;
  }

  protected onPanelTabIndexChange(index: number): void {
    const order: AgentPanelTab[] = ['result', 'code', 'api'];
    const nextTab = order[index];
    if (nextTab) {
      this.store.dispatch(agentActions.setPanelTab({ tab: nextTab }));
    }
  }

  protected highlightCode(code: string | undefined, language: string | undefined): SafeHtml {
    if (!code) {
      return this.sanitizer.bypassSecurityTrustHtml('');
    }
    const lang = language && language.length > 0 ? language : 'java';
    try {
      const highlighted = hljs.highlight(code, { language: lang }).value;
      return this.sanitizer.bypassSecurityTrustHtml(highlighted);
    } catch {
      return this.sanitizer.bypassSecurityTrustHtml(this.escapeHtml(code));
    }
  }

  private escapeHtml(text: string): string {
    return text
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
  }

  protected stringifyClassName(value: unknown): string {
    return typeof value === 'string' && value.length > 0 ? value : 'Type';
  }

  protected stringifyCode(value: unknown): string | undefined {
    if (typeof value === 'string') {
      return value;
    }
    return undefined;
  }

  protected stringLang(value: unknown): string | undefined {
    if (typeof value === 'string') {
      return value;
    }
    return undefined;
  }

  protected metadataLines(metadata: unknown): { label: string; value: string }[] {
    if (!metadata || typeof metadata !== 'object' || Array.isArray(metadata)) {
      return [];
    }
    const record = metadata as Record<string, unknown>;
    const order = ['title', 'method', 'path', 'baseUrl', 'auth'];
    const lines: { label: string; value: string }[] = [];
    for (const key of order) {
      const value = record[key];
      if (value !== undefined && value !== null && String(value).length > 0) {
        lines.push({ label: key, value: String(value) });
      }
    }
    for (const [key, value] of Object.entries(record)) {
      if (order.includes(key)) {
        continue;
      }
      if (value !== undefined && value !== null && String(value).length > 0) {
        lines.push({ label: key, value: String(value) });
      }
    }
    return lines;
  }

  protected copyText(text: string | undefined): void {
    if (!text) {
      return;
    }
    void navigator.clipboard.writeText(text);
  }

  protected downloadSource(filename: string, content: string | undefined): void {
    if (!content) {
      return;
    }
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(objectUrl);
  }

  protected inputsValid(): boolean {
    return this.goalText.trim().length > 0 && this.openApiText.trim().length > 0;
  }

  protected run(): void {
    if (!this.inputsValid() || this.loading()) {
      return;
    }
    this.store.dispatch(
      agentActions.runAgent({
        request: { goal: this.goalText.trim(), openApiSpec: this.openApiText.trim() },
      }),
    );
  }

  protected retry(): void {
    const previous = this.lastRequest();
    if (!previous || this.loading()) {
      return;
    }
    this.goalText = previous.goal;
    this.openApiText = previous.openApiSpec;
    this.store.dispatch(agentActions.runAgent({ request: previous }));
  }
}
