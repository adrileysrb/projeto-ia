import { Message } from '@ag-ui/client';
import { createChatItemsMapper } from './chat-items';

const CHART = JSON.stringify({
  kind: 'chart',
  chartType: 'BAR',
  title: 'Total por usuário',
  subtitle: 'Valor total em reais',
  metric: 'TOTAL_AMOUNT',
  labels: ['Ana', 'Bruno'],
  labelSlots: [0, 1],
  series: [{ name: 'Valor total', slot: 0, data: [10, 20] }],
});
const STAT = JSON.stringify({
  kind: 'stat',
  title: 'Pix em outubro',
  subtitle: 'Valor total em reais · Pix',
  metric: 'TOTAL_AMOUNT',
  value: 10087.47,
  comparison: null,
});
const TABLE = JSON.stringify({
  kind: 'table',
  title: 'Maiores transações',
  subtitle: 'Maiores valores primeiro',
  rows: [{ date: '15/10/2026 20:34', user: 'Ana Souza', type: 'Cartão', category: 'Saúde', status: 'Concluída', amount: 4925.14 }],
  totalRows: 45,
});

const USER: Message = { id: 'm1', role: 'user', content: 'faz um gráfico' };
const TOOL_CALL: Message = {
  id: 'm2',
  role: 'assistant',
  toolCalls: [{ id: 'call-1', type: 'function', function: { name: 'renderChart', arguments: '{}' } }],
};
const TOOL_RESULT: Message = { id: 'm3', role: 'tool', toolCallId: 'call-1', content: CHART };
const COMMENT: Message = { id: 'm4', role: 'assistant', content: 'Bruno lidera.' };

describe('createChatItemsMapper', () => {
  it('should turn a full exchange into user text, chart and assistant text', () => {
    const items = createChatItemsMapper()([USER, TOOL_CALL, TOOL_RESULT, COMMENT], false);

    expect(items.map((item) => item.kind)).toEqual(['user', 'chart', 'assistant']);
    expect(items[1]).toMatchObject({ id: 'm3', spec: { title: 'Total por usuário' } });
    expect(items[2]).toMatchObject({ text: 'Bruno lidera.' });
  });

  it('should show a number and a table according to the kind of the tool result', () => {
    const items = createChatItemsMapper()(
      [
        USER,
        { id: 'm3', role: 'tool', toolCallId: 'call-1', content: STAT },
        { id: 'm5', role: 'tool', toolCallId: 'call-2', content: TABLE },
      ],
      false,
    );

    expect(items.map((item) => item.kind)).toEqual(['user', 'stat', 'table']);
    expect(items[1]).toMatchObject({ id: 'm3', spec: { value: 10087.47 } });
    expect(items[2]).toMatchObject({ id: 'm5', spec: { totalRows: 45 } });
  });

  it('should show a pending tool call only while the run is in progress', () => {
    const toItems = createChatItemsMapper();

    expect(toItems([USER, TOOL_CALL], true).map((item) => item.kind)).toEqual(['user', 'tool-pending']);
    expect(toItems([USER, TOOL_CALL], false).map((item) => item.kind)).toEqual(['user']);
  });

  it('should hide a tool result that is not something to show', () => {
    const failed: Message = { id: 'm3', role: 'tool', toolCallId: 'call-1', content: 'Nenhuma transação encontrada.' };
    const unknown: Message = { id: 'm6', role: 'tool', toolCallId: 'call-3', content: '{"kind":"mapa","title":"x"}' };

    const items = createChatItemsMapper()([USER, TOOL_CALL, failed, unknown], true);

    expect(items.map((item) => item.kind)).toEqual(['user']);
  });

  it('should keep the same chart object across updates so the chart is not redrawn', () => {
    const toItems = createChatItemsMapper();

    const first = toItems([USER, TOOL_CALL, TOOL_RESULT], true);
    const second = toItems([USER, TOOL_CALL, { ...TOOL_RESULT }, { ...COMMENT, content: 'Bru' }], true);

    expect(first[1].kind).toBe('chart');
    expect(second[1]).toMatchObject({ kind: 'chart' });
    expect((second[1] as { spec: unknown }).spec).toBe((first[1] as { spec: unknown }).spec);
  });
});
