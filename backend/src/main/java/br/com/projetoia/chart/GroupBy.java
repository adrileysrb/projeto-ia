package br.com.projetoia.chart;

public enum GroupBy {
    TYPE,
    USER,
    CATEGORY,
    STATUS,
    DAY,
    WEEK,
    MONTH;

    public boolean isTime() {
        return this == DAY || this == WEEK || this == MONTH;
    }
}
