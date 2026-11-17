import { parseSuggestions } from './chat-agent';

describe('parseSuggestions', () => {
  it('should keep the questions the server sends', () => {
    expect(parseSuggestions(['Qual o total por tipo?', 'Como foi outubro?'])).toEqual([
      'Qual o total por tipo?',
      'Como foi outubro?',
    ]);
  });

  it('should drop anything that is not a non-empty text', () => {
    expect(parseSuggestions(['Qual o total?', '', '  ', 42, null, { text: 'x' }])).toEqual(['Qual o total?']);
  });

  it('should return no suggestions when the value is not a list', () => {
    expect(parseSuggestions('Qual o total?')).toEqual([]);
    expect(parseSuggestions(undefined)).toEqual([]);
  });
});
