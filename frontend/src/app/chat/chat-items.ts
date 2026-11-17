import { Message } from '@ag-ui/client';
import { parseVisual, Visual } from '../visual/visual';

/** O que a tela mostra, derivado das mensagens do protocolo. */
export type ChatItem =
  | { kind: 'user'; id: string; text: string }
  | { kind: 'assistant'; id: string; text: string }
  | ({ id: string } & Visual)
  | { kind: 'tool-pending'; id: string };

/**
 * Cria a função que traduz mensagens AG-UI em itens do chat. Os resultados já interpretados
 * ficam em cache: a lista é recalculada a cada token recebido, e sem o cache cada token
 * criaria um objeto novo e faria o gráfico ser redesenhado.
 */
export function createChatItemsMapper(): (messages: readonly Message[], running: boolean) => ChatItem[] {
  const visualCache = new Map<string, { content: string; visual: Visual | null }>();

  const visualOf = (id: string, content: string): Visual | null => {
    const cached = visualCache.get(id);
    if (cached?.content === content) {
      return cached.visual;
    }
    const visual = parseVisual(content);
    visualCache.set(id, { content, visual });
    return visual;
  };

  return (messages, running) => {
    const answeredToolCalls = new Set(
      messages.flatMap((message) => (message.role === 'tool' ? [message.toolCallId] : [])),
    );
    const items: ChatItem[] = [];

    for (const message of messages) {
      if (message.role === 'user') {
        items.push({ kind: 'user', id: message.id, text: textOf(message.content) });
      } else if (message.role === 'assistant') {
        const text = textOf(message.content);
        if (text.trim()) {
          items.push({ kind: 'assistant', id: message.id, text });
        }
        // Uma chamada sem resultado só está "em andamento" enquanto a execução roda.
        for (const toolCall of message.toolCalls ?? []) {
          if (running && !answeredToolCalls.has(toolCall.id)) {
            items.push({ kind: 'tool-pending', id: toolCall.id });
          }
        }
      } else if (message.role === 'tool') {
        // Resultado que não é para mostrar (erro devolvido ao modelo) não aparece:
        // o modelo tenta de novo ou explica em texto.
        const visual = visualOf(message.id, textOf(message.content));
        if (visual) {
          items.push({ id: message.id, ...visual });
        }
      }
    }
    return items;
  };
}

function textOf(content: unknown): string {
  if (typeof content === 'string') {
    return content;
  }
  if (Array.isArray(content)) {
    return content
      .map((part) => (part && typeof part === 'object' && 'text' in part && typeof part.text === 'string' ? part.text : ''))
      .join('');
  }
  return '';
}
