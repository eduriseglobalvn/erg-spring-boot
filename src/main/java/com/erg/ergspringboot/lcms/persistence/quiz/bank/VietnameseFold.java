package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Accent-insensitive matching of Vietnamese text: "bieu tuong" finds "biểu tượng". The database has no unaccent extension. */
public final class VietnameseFold {

    static final String FROM = "àáãảạăằắẵẳặâầấẫẩậèéẽẻẹêềếễểệìíĩỉịòóõỏọôồốỗổộơờớỡởợùúũủụưừứữửựỳýỹỷỵđÀÁÃẢẠĂẰẮẴẲẶÂẦẤẪẨẬÈÉẼẺẸÊỀẾỄỂỆÌÍĨỈỊÒÓÕỎỌÔỒỐỖỔỘƠỜỚỠỞỢÙÚŨỦỤƯỪỨỮỬỰỲÝỸỶỴĐ";
    static final String TO = "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyydaaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd";
    // translate() drops the characters of its second argument that have no partner: the combining tone and vowel marks of decomposed text
    private static final String MARKS_SQL = "\u0300\u0301\u0303\u0309\u0323\u0302\u0306\u031B";
    /** JPQL condition on a question {@code q}: its stem contains {@code :search}, which the caller has passed through {@link #fold}. */
    public static final String STEM_CONTAINS_SEARCH = " lower(function('translate', coalesce(q.stem, ''), '" + FROM + MARKS_SQL + "', '" + TO
            + "')) like concat('%', :search, '%')";
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private VietnameseFold() {
    }

    public static String fold(String text) {
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase(Locale.ROOT);
    }
}
