import { afterRenderEffect, Component, computed, DestroyRef, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PieController,
  PointElement,
  Tooltip,
} from 'chart.js';
import { TreemapController, TreemapElement } from 'chartjs-chart-treemap';
import { buildChartConfig, readChartTheme } from './chart-config';
import { formatValue } from './chart-format';
import { ChartSpec } from './chart-spec';

// Só o que os tipos de gráfico usam, para o restante do Chart.js ficar fora do bundle.
Chart.register(
  PieController,
  ArcElement,
  BarController,
  BarElement,
  LineController,
  LineElement,
  PointElement,
  TreemapController,
  TreemapElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
);

/** Altura de cada barra horizontal com seu respiro, e da faixa do eixo de valores. */
const ROW_HEIGHT = 40;
const VALUE_AXIS_HEIGHT = 48;

@Component({
  selector: 'app-chart-view',
  templateUrl: './chart-view.html',
  styleUrl: './chart-view.css',
})
export class ChartView {
  readonly spec = input.required<ChartSpec>();

  /** A altura inclui a faixa do eixo e a legenda, que o Chart.js desenha dentro do canvas. */
  protected readonly plotHeight = computed(() => {
    const spec = this.spec();
    switch (spec.chartType) {
      case 'PIE':
      case 'TREEMAP':
        return 300;
      case 'HORIZONTAL_BAR':
      case 'FUNNEL':
        // Barras horizontais: a altura acompanha a quantidade de barras.
        return spec.labels.length * ROW_HEIGHT + VALUE_AXIS_HEIGHT;
      default:
        return 280;
    }
  });

  protected readonly rows = computed(() => {
    const spec = this.spec();
    return spec.labels.map((label, index) => ({
      label,
      values: spec.series.map((series) => formatValue(series.data[index], spec.metric)),
    }));
  });

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  /** Muda quando o sistema troca entre tema claro e escuro, para o gráfico ser redesenhado. */
  private readonly colorScheme = signal(0);
  private chart?: Chart;

  constructor() {
    const darkMode = matchMedia('(prefers-color-scheme: dark)');
    const onSchemeChange = () => this.colorScheme.update((version) => version + 1);
    darkMode.addEventListener('change', onSchemeChange);

    afterRenderEffect(() => {
      this.colorScheme();
      const canvas = this.canvas().nativeElement;
      this.chart?.destroy();
      this.chart = new Chart(canvas, buildChartConfig(this.spec(), readChartTheme(canvas)));
    });

    inject(DestroyRef).onDestroy(() => {
      darkMode.removeEventListener('change', onSchemeChange);
      this.chart?.destroy();
    });
  }
}
