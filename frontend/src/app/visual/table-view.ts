import { Component, computed, input } from '@angular/core';
import { formatValue } from '../chart/chart-format';
import { TableSpec } from './table-spec';

/** Tabela de transações individuais. */
@Component({
  selector: 'app-table-view',
  templateUrl: './table-view.html',
  styleUrl: './table-view.css',
})
export class TableView {
  readonly spec = input.required<TableSpec>();

  protected readonly rows = computed(() =>
    this.spec().rows.map((row) => ({ ...row, amount: formatValue(row.amount, 'TOTAL_AMOUNT') })),
  );

  /** Quantas linhas estão na tela em relação a quantas existem, para a lista cortada não parecer completa. */
  protected readonly summary = computed(() => {
    const { rows, totalRows } = this.spec();
    const noun = totalRows === 1 ? 'transação' : 'transações';
    return rows.length < totalRows ? `Mostrando ${rows.length} de ${totalRows} ${noun}` : `${totalRows} ${noun}`;
  });
}
