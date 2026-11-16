package com.erg.ergspringboot.calendar;

/** Display name of a teacher in dense schedule cells: "Nguyễn Quốc Vương" becomes "Q.Vương". Pure. */
public final class TeacherShortName {

    private TeacherShortName() {
    }

    // shortcut: only the last two words are used, so "Nguyễn Thị Lan Anh" becomes "L.Anh"; upgrade if HR wants compound given names kept.
    public static String of(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] words = fullName.trim().split("\s+");
        if (words.length < 3) return String.join(" ", words);
        String middle = words[words.length - 2];
        return Character.toUpperCase(middle.charAt(0)) + "." + words[words.length - 1];
    }

    /** Avatar letters: initials of the last two words ("Nguyễn Quốc Vương" becomes "QV"), one letter for a single word. */
    public static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String[] words = fullName.trim().split(" +");
        StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, words.length - 2); i < words.length; i++) out.append(Character.toUpperCase(words[i].charAt(0)));
        return out.toString();
    }
}
