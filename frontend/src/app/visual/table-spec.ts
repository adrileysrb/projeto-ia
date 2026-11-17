import { isFiniteNumber, isRecord, isString, toRecord } from './guards';

/** Uma transação, com os textos já no formato de exibição. */
export interface TableRow {
  date: string;
  user: string;
  type: string;
  category: string;
  status: string;
  amount: number;
}

/** Lista de transações montada pelo backend. */
export interface TableSpec {
  title: string;
  subtitle: string;
  rows: TableRow[];
  /** Quantas transações atendem ao filtro, que pode ser mais do que as linhas mostradas. */
  totalRows: number;
}

export function parseTableSpec(content: unknown): TableSpec | null {
  const value = toRecord(content);
  if (!value) {
    return null;
  }
  const { title, subtitle, rows, totalRows } = value;
  if (!Array.isArray(rows) || rows.length === 0) {
    return null;
  }

  const parsedRows: TableRow[] = [];
  for (const row of rows) {
    if (!isRecord(row) || !isFiniteNumber(row['amount'])) {
      return null;
    }
    const text = (field: string) => (isString(row[field]) ? row[field] : '');
    parsedRows.push({
      date: text('date'),
      user: text('user'),
      type: text('type'),
      category: text('category'),
      status: text('status'),
      amount: row['amount'],
    });
  }

  return {
    title: isString(title) ? title : 'Transações',
    subtitle: isString(subtitle) ? subtitle : '',
    rows: parsedRows,
    totalRows: isFiniteNumber(totalRows) && totalRows >= parsedRows.length ? totalRows : parsedRows.length,
  };
}
