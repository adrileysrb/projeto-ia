import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Message } from '@ag-ui/client';
import { vi } from 'vitest';
import { Chat } from './chat';
import { ChatAgent } from './chat-agent';

describe('Chat', () => {
  let fixture: ComponentFixture<Chat>;
  let element: HTMLElement;
  const agent = {
    messages: signal<readonly Message[]>([]),
    running: signal(false),
    error: signal<string | null>(null),
    suggestions: signal<readonly string[]>([]),
    send: vi.fn(),
  };

  beforeEach(async () => {
    // O jsdom não implementa a rolagem.
    Element.prototype.scrollIntoView = vi.fn();
    agent.messages.set([]);
    agent.running.set(false);
    agent.error.set(null);
    agent.suggestions.set([]);
    agent.send.mockReset();

    await TestBed.configureTestingModule({
      imports: [Chat],
      providers: [{ provide: ChatAgent, useValue: agent }],
    }).compileComponents();
    fixture = TestBed.createComponent(Chat);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  it('should offer suggestions and send the one that is clicked', async () => {
    const suggestions = element.querySelectorAll<HTMLButtonElement>('.suggestion');
    expect(suggestions.length).toBe(4);

    suggestions[0].click();

    expect(agent.send).toHaveBeenCalledWith(suggestions[0].textContent?.trim());
  });

  it('should send the typed message on Enter and clear the field', async () => {
    const textarea = element.querySelector('textarea')!;
    textarea.value = 'total por tipo em pizza';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();

    expect(agent.send).toHaveBeenCalledWith('total por tipo em pizza');
    expect(textarea.value).toBe('');
  });

  it('should not send while a response is in progress', async () => {
    agent.running.set(true);
    await fixture.whenStable();

    const textarea = element.querySelector('textarea')!;
    textarea.value = 'outra pergunta';
    textarea.dispatchEvent(new Event('input'));
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(agent.send).not.toHaveBeenCalled();
    expect(element.querySelector<HTMLButtonElement>('.composer button')!.disabled).toBe(true);
  });

  it('should render the conversation and the waiting states', async () => {
    agent.messages.set([{ id: 'm1', role: 'user', content: 'oi' }]);
    agent.running.set(true);
    await fixture.whenStable();

    expect(element.querySelector('.message.user')?.textContent).toBe('oi');
    expect(element.querySelector('.status')?.textContent).toContain('Pensando');

    agent.messages.set([
      { id: 'm1', role: 'user', content: 'oi' },
      { id: 'm2', role: 'assistant', content: 'Olá!' },
    ]);
    agent.running.set(false);
    await fixture.whenStable();

    expect(element.querySelector('.message.assistant')?.textContent).toBe('Olá!');
    expect(element.querySelector('.status')).toBeNull();
    expect(element.querySelector('.suggestion')).toBeNull();
  });

  it('should render a number and a table sent as tool results', async () => {
    const stat = {
      kind: 'stat',
      title: 'Pix em outubro',
      subtitle: 'Valor total em reais · Pix',
      metric: 'TOTAL_AMOUNT',
      value: 10087.47,
      comparison: { period: '01/09/2026 a 30/09/2026', value: 6240.43, changePercent: 61.6 },
    };
    const table = {
      kind: 'table',
      title: 'Maiores transações',
      subtitle: 'Maiores valores primeiro',
      rows: [{ date: '15/10/2026 20:34', user: 'Ana Souza', type: 'Cartão', category: 'Saúde', status: 'Concluída', amount: 4925.14 }],
      totalRows: 45,
    };
    agent.messages.set([
      { id: 'm1', role: 'user', content: 'quanto de pix em outubro?' },
      { id: 'm2', role: 'tool', toolCallId: 'call-1', content: JSON.stringify(stat) },
      { id: 'm3', role: 'tool', toolCallId: 'call-2', content: JSON.stringify(table) },
    ]);
    await fixture.whenStable();

    const card = element.querySelector('app-stat-card')!;
    expect(card.querySelector('.value')?.textContent).toBe('R$ 10.087,47');
    expect(card.querySelector('.comparison')?.textContent).toContain('61,6% acima de 01/09/2026 a 30/09/2026');

    const tableView = element.querySelector('app-table-view')!;
    expect(tableView.querySelectorAll('tbody tr').length).toBe(1);
    expect(tableView.querySelector('td.amount')?.textContent).toBe('R$ 4.925,14');
    expect(tableView.querySelector('.summary')?.textContent).toBe('Mostrando 1 de 45 transações');
  });

  it('should offer the follow-up questions of the last answer and send the one that is clicked', async () => {
    agent.messages.set([
      { id: 'm1', role: 'user', content: 'total por categoria' },
      { id: 'm2', role: 'assistant', content: 'Saúde lidera.' },
    ]);
    agent.suggestions.set(['Quais as maiores transações de Saúde?', 'Como Saúde evoluiu por mês?']);
    await fixture.whenStable();

    const followUps = element.querySelectorAll<HTMLButtonElement>('.follow-up');
    expect(Array.from(followUps, (button) => button.textContent?.trim())).toEqual([
      'Quais as maiores transações de Saúde?',
      'Como Saúde evoluiu por mês?',
    ]);

    followUps[1].click();

    expect(agent.send).toHaveBeenCalledWith('Como Saúde evoluiu por mês?');
  });

  it('should hide the follow-up questions while a new answer is in progress', async () => {
    agent.messages.set([{ id: 'm1', role: 'user', content: 'oi' }]);
    agent.suggestions.set(['Qual o total por tipo?']);
    agent.running.set(true);
    await fixture.whenStable();

    expect(element.querySelector('.follow-up')).toBeNull();
  });

  it('should show the error reported by the agent', async () => {
    agent.error.set('Connection refused');
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Connection refused');
  });
});
