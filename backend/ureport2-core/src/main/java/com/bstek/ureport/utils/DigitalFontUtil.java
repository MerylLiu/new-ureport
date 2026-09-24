package com.bstek.ureport.utils;

import com.bstek.ureport.export.pdf.font.FontBuilder;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.renderer.IRenderer;
import com.itextpdf.layout.renderer.TextRenderer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DigitalFontUtil {

    private static final Pattern PATTERN = Pattern.compile("([0-9.]+)|([^0-9.]+)");

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9.]+");

    /**
     * 数字用 Times New Roman、其余用中文字体，按此规则把文本拆成多个 Text 追加到段落。
     */
    public static void appendMixedText(Paragraph paragraph, String text, PdfFont fontCn) {
        PdfFont numFont = null;
        try {
            numFont = FontBuilder.getFont("Times New Roman", 12, false, false, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        append(paragraph, text, fontCn, numFont);
    }

    /**
     * 不区分数字字体，统一使用指定字体把文本追加到段落（页眉页脚等复用）。
     */
    public static void appendPlainText(Paragraph paragraph, String text, PdfFont font) {
        append(paragraph, text, font, null);
    }

    /**
     * iText 7 在布局每一行时会调用 {@link TextRenderer#trimFirst()} 把行首空白
     * （{@code Character.isWhitespace} 为真的半角空格、全角空格、制表符等）整段丢掉，
     * 用空格/全角空格做出来的行首缩进在 PDF 中会消失，而 iText 5 不会裁剪。
     * 另外同一个 Text 内部的换行会由 iText 另建渲染器，那部分渲染器不受自定义渲染器控制，
     * 第二行以后的行首空白依旧会被裁掉，因此这里按换行手工拆分，再对每个 Text 使用
     * 不裁剪的渲染器。
     */
    private static void append(Paragraph paragraph, String text, PdfFont fontCn, PdfFont numFont) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            // 仅在「行 i 实际有可见内容时」才追加换行分隔符，
            // 末尾由结尾换行带来的空字符串（split limit=-1 保留）不追加，
            // 避免单元格文本结尾的 \n / \r\n 多渲染出一行空白。
            boolean isTrailingEmpty = (i == lines.length - 1 && lines[i].isEmpty());
            if (i > 0 && !isTrailingEmpty) {
                paragraph.add(newText("\n", fontCn));
            }
            if (lines[i].isEmpty()) {
                continue;
            }
            if (numFont == null) {
                paragraph.add(newText(lines[i], fontCn));
                continue;
            }
            Matcher matcher = PATTERN.matcher(lines[i]);
            while (matcher.find()) {
                String seg = matcher.group();
                if (NUMBER_PATTERN.matcher(seg).matches()) {
                    paragraph.add(newText(seg, numFont));
                } else {
                    paragraph.add(newText(seg, fontCn));
                }
            }
        }
    }

    private static Text newText(String content, PdfFont font) {
        Text text = new Text(content).setFont(font);
        text.setNextRenderer(new NoTrimTextRenderer(text));
        return text;
    }

    /**
     * 不裁剪行首空白的 TextRenderer。
     */
    static class NoTrimTextRenderer extends TextRenderer {

        NoTrimTextRenderer(Text modelElement) {
            super(modelElement);
        }

        /**
         * 同 {@code CellBorderEvent.BorderCellRenderer}：iText 7 元素的 nextRenderer 是一条
         * 一次性链条，{@code getRenderer()} 取出当前渲染器后会把 nextRenderer 换成
         * {@code getNextRenderer()} 的返回值，而 {@code TextRenderer.getNextRenderer()}
         * 默认 new 出一个普通 TextRenderer（会裁剪行首空白）。
         * 除表格真正渲染外，任何额外的布局（如 PDF 侧实测单元格内容高度）也会消耗一次，
         * 若不覆写，真正渲染时就退化成普通渲染器、行首空格再次被裁掉。
         */
        @Override
        public IRenderer getNextRenderer() {
            return new NoTrimTextRenderer((Text) getModelElement());
        }

        @Override
        public void trimFirst() {
            // 保留行首空白，不做裁剪
        }
    }
}
