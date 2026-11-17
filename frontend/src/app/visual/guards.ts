/** Verificações de tipo usadas para ler os resultados das tools, que chegam como JSON sem garantia de formato. */

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function isString(value: unknown): value is string {
  return typeof value === 'string';
}

export function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

export function isArrayOf<T>(value: unknown, guard: (item: unknown) => item is T): value is T[] {
  return Array.isArray(value) && value.every(guard);
}

/** Aceita o objeto já interpretado ou o texto JSON. Devolve `null` quando não é um objeto JSON. */
export function toRecord(content: unknown): Record<string, unknown> | null {
  let value: unknown = content;
  if (typeof content === 'string') {
    try {
      value = JSON.parse(content);
    } catch {
      return null;
    }
  }
  return isRecord(value) ? value : null;
}
