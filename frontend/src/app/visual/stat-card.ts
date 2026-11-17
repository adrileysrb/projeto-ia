import { Component, computed, input } from '@angular/core';
import { formatValue } from '../chart/chart-format';
import { StatSpec, summarizeComparison } from './stat-spec';

/** Número em destaque, com a comparação contra o período anterior quando houver. */
@Component({
  selector: 'app-stat-card',
  templateUrl: './stat-card.html',
  styleUrl: './stat-card.css',
})
export class StatCard {
  readonly spec = input.required<StatSpec>();

  protected readonly value = computed(() => formatValue(this.spec().value, this.spec().metric));
  protected readonly comparison = computed(() => summarizeComparison(this.spec()));
}
