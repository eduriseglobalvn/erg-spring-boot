package com.erg.ergspringboot.timekeeping.internal.admin;

import java.time.LocalDate;

/**
 * Solar to Vietnamese lunar date (time zone UTC+7), after Ho Ngoc Duc's astronomical algorithm: new moons and the
 * sun's longitude decide the months, the month holding the winter solstice is the 11th and a year of 13 months gets
 * its leap month. Valid for the years the calendar is used for (1900-2199).
 */
final class VietnameseLunarCalendar {

    private static final double ZONE = 7.0;
    private static final double SYNODIC = 29.530588853;

    private VietnameseLunarCalendar() {}

    /** {@code day/month}, with a trailing " nhuận" inside a leap month: "10/3", "1/1", "5/4 nhuận". */
    static String format(LocalDate date) {
        int[] lunar = convert(date);
        return lunar[0] + "/" + lunar[1] + (lunar[3] == 1 ? " nhuận" : "");
    }

    /** {day, month, year, leap (0/1)} */
    static int[] convert(LocalDate date) {
        int dayNumber = jd(date);
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / SYNODIC);
        int monthStart = newMoonDay(k + 1);
        if (monthStart > dayNumber) monthStart = newMoonDay(k);
        int a11 = lunarMonth11(date.getYear());
        int b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = date.getYear();
            a11 = lunarMonth11(date.getYear() - 1);
        } else {
            lunarYear = date.getYear() + 1;
            b11 = lunarMonth11(date.getYear() + 1);
        }
        int lunarDay = dayNumber - monthStart + 1;
        int diff = (int) Math.floor((monthStart - a11) / 29.0);
        int leap = 0;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapMonthDiff = leapMonthOffset(a11);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                if (diff == leapMonthDiff) leap = 1;
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
        return new int[] {lunarDay, lunarMonth, lunarYear, leap};
    }

    private static int jd(LocalDate date) {
        return (int) (date.toEpochDay() + 2440588L);
    }

    private static double newMoon(int k) {
        double t = k / 1236.85;
        double t2 = t * t;
        double t3 = t2 * t;
        double dr = Math.PI / 180;
        double jd1 = 2415020.75933 + SYNODIC * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr);
        double m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        double mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        double f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;
        double c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m);
        c1 = c1 - 0.4068 * Math.sin(mpr * dr) + 0.0161 * Math.sin(dr * 2 * mpr);
        c1 = c1 - 0.0004 * Math.sin(dr * 3 * mpr);
        c1 = c1 + 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr));
        c1 = c1 - 0.0074 * Math.sin(dr * (m - mpr)) + 0.0004 * Math.sin(dr * (2 * f + m));
        c1 = c1 - 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr));
        c1 = c1 + 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m));
        double deltaT = t < -11
                ? 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
                : -0.000278 + 0.000265 * t + 0.000262 * t2;
        return jd1 + c1 - deltaT;
    }

    private static double sunLongitude(double jdn) {
        double t = (jdn - 2451545.0) / 36525;
        double t2 = t * t;
        double dr = Math.PI / 180;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m);
        dl = dl + (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double l = (l0 + dl) * dr;
        return l - Math.PI * 2 * Math.floor(l / (Math.PI * 2));
    }

    private static int sunSector(int dayNumber) {
        return (int) Math.floor(sunLongitude(dayNumber - 0.5 - ZONE / 24.0) / Math.PI * 6);
    }

    private static int newMoonDay(int k) {
        return (int) Math.floor(newMoon(k) + 0.5 + ZONE / 24.0);
    }

    private static int lunarMonth11(int year) {
        int off = jd(LocalDate.of(year, 12, 31)) - 2415021;
        int k = (int) Math.floor(off / SYNODIC);
        int nm = newMoonDay(k);
        if (sunSector(nm) >= 9) nm = newMoonDay(k - 1);
        return nm;
    }

    private static int leapMonthOffset(int a11) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / SYNODIC + 0.5);
        int i = 1;
        int arc = sunSector(newMoonDay(k + i));
        int last;
        do {
            last = arc;
            i++;
            arc = sunSector(newMoonDay(k + i));
        } while (arc != last && i < 14);
        return i - 1;
    }
}
