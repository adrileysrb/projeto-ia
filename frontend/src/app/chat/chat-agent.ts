import { Injectable, signal } from '@angular/core';
import { HttpAgent, Message } from '@ag-ui/client';

/** Nome do evento CUSTOM em que o backend envia as próximas perguntas sugeridas. */
const SUGGESTIONS_EVENT = 'suggestions';

/** O valor do evento vem do servidor sem garantia de formato: ficam só os textos não vazios. */
export function parseSuggestions(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter((item): item is string => typeof item === 'string' && item.trim() !== '');
}

/**
 * Conversa com o backend pelo protocolo AG-UI. O HttpAgent envia o histórico em cada
 * execução, lê o stream de eventos e mantém a lista de mensagens; aqui ela vira signals.
 */
@Injectable({ providedIn: 'root' })
export class ChatAgent {
  private readonly agent = new HttpAgent({ url: '/api/agent', threadId: crypto.randomUUID() });

  readonly messages = signal<readonly Message[]>([]);
  readonly running = signal(false);
  readonly error = signal<string | null>(null);
  /** Próximas perguntas sugeridas pelo modelo para a última resposta. */
  readonly suggestions = signal<readonly string[]>([]);

  constructor() {
    this.agent.subscribe({
      onMessagesChanged: ({ messages }) => this.messages.set([...messages]),
      onRunErrorEvent: ({ event }) => this.error.set(event.message),
      // As sugestões não fazem parte das mensagens: chegam num evento CUSTOM do AG-UI.
      onCustomEvent: ({ event }) => {
        if (event.name === SUGGESTIONS_EVENT) {
          this.suggestions.set(parseSuggestions(event.value));
        }
      },
    });
  }

  async send(text: string): Promise<void> {
    const content = text.trim();
    if (!content || this.running()) {
      return;
    }

    this.error.set(null);
    // As sugestões valem só para a resposta anterior.
    this.suggestions.set([]);
    this.running.set(true);
    this.agent.addMessage({ id: crypto.randomUUID(), role: 'user', content });
    this.messages.set([...this.agent.messages]);

    try {
      await this.agent.runAgent();
    } catch (error) {
      // Um RUN_ERROR do servidor já preencheu a mensagem; aqui ficam as falhas de rede.
      if (this.error() === null) {
        this.error.set(error instanceof Error ? error.message : String(error));
      }
    } finally {
      this.running.set(false);
    }
  }
}
