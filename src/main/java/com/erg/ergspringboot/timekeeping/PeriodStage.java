package com.erg.ergspringboot.timekeeping;

/** Month sheet stage: still collecting (month not over), under review (over, not locked), locked. */
public enum PeriodStage {
    COLLECT, REVIEW, LOCKED
}
