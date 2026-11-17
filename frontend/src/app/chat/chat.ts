import { afterRenderEffect, Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { ChartView } from '../chart/chart-view';
import { StatCard } from '../visual/stat-card';
import { TableView } from '../visual/table-view';
import { ChatAgent } from './chat-agent';
import { createChatItemsMapper } from './chat-items';

@Component({
  selector: 'app-chat',
  imports: [ChartView, StatCard, TableView],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class Chat {
  private readonly agent = inject(ChatAgent);
  private readonly toItems = createChatItemsMapper();
  private readonly end = viewChild.required<ElementRef<HTMLElement>>('end');
  private readonly input = viewChild.required<ElementRef<HTMLTextAreaElement>>('input');

  protected readonly draft = signal('');
  protected readonly running = this.agent.running;
  protected readonly error = this.agent.error;
  protected readonly items = computed(() => this.toItems(this.agent.messages(), this.agent.running()));

  /** Enquanto o modelo ainda não começou a responder (ou pensa depois de mostrar um resultado). */
  protected readonly thinking = computed(() => {
    const last = this.items().at(-1);
    return this.running() && last !== undefined && last.kind !== 'assistant' && last.kind !== 'tool-pending';
  });

  /** Próximas perguntas sugeridas pelo modelo; só aparecem com a resposta concluída. */
  protected readonly followUps = computed(() => (this.running() ? [] : this.agent.suggestions()));

  // Uma sugestão para cada forma de resposta: número, composição, evolução e lista.
  protected readonly suggestions = [
    'Quanto foi movimentado em outubro?',
    'Faça um gráfico de pizza com o valor total por categoria',
    'Mostre em barras empilhadas o valor de cada mês dividido por tipo',
    'Quais foram as 5 maiores transações?',
  ];

  constructor() {
    // Mantém o fim da conversa à vista conforme o texto, os gráficos e as sugestões chegam.
    afterRenderEffect(() => {
      this.followUps();
      if (this.items().length > 0) {
        this.end().nativeElement.scrollIntoView({ block: 'end' });
      }
    });
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.send(this.draft());
  }

  protected send(text: string): void {
    if (!text.trim() || this.running()) {
      return;
    }
    // O campo é limpo direto no elemento: o signal só espelha o texto para habilitar o botão.
    this.input().nativeElement.value = '';
    this.draft.set('');
    void this.agent.send(text);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send(this.draft());
    }
  }

  protected onInput(event: Event): void {
    this.draft.set((event.target as HTMLTextAreaElement).value);
  }
}
