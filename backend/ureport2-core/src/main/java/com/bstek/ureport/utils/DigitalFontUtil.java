package com.bstek.ureport.utils;

import com.bstek.ureport.export.pdf.font.FontBuilder;
import com.itextpdf.text.Font;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.FontSelector;

public class DigitalFontUtil {
    private static FontSelector globalSelector;

    public static Phrase getPhrase(String text, Font fontCn) {
        globalSelector = new FontSelector();
        try {
            Font fontNum = FontBuilder.getFont("Times New Roman", (int) fontCn.getSize(), fontCn.isBold(), fontCn.isItalic(), fontCn.isUnderlined());

            globalSelector.addFont(fontNum);
            globalSelector.addFont(fontCn);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return globalSelector.process(text);
    }
}
