package com.bstek.ureport.utils;

import com.bstek.ureport.export.pdf.font.FontBuilder;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.renderer.IRenderer;
import com.itextpdf.layout.renderer.TextRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DigitalFontUtil {

    private static final Pattern PATTERN = Pattern.compile("([0-9.]+)|([^0-9.]+)");

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9.]+");

    // 禁止出现在行首的标点（句末、右括号、右引号等），与 ChineseSplitCharacter 保持一致
    private static final String FORBIDDEN_LINE_START = "，。、；：？！）】》」』〕\u201D\u2019〉";

    // 禁止出现在行尾的标点（左括号、左引号等），与 ChineseSplitCharacter 保持一致
    private static final String FORBIDDEN_LINE_END = "（【《「『〔{\u201C\u2018〈";

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
            // 统一按数字/非数字分段并做边界禁排修正：
            // 即使不区分数字字体（页眉页脚等 appendPlainText 场景），也要分段——
            // iText 7 对超行宽的不可断块会在第一个放不下的字符前强制断行，
            // 此路径完全绕过 ISplitCharacters，长数字串+标点的整块文本会因此
            // 把标点悬在行末；拆成多元素后元素边界成为合法断点，可避免强制断行
            Matcher matcher = PATTERN.matcher(lines[i]);
            List<String[]> segments = new ArrayList<String[]>();
            while (matcher.find()) {
                String seg = matcher.group();
                boolean isNum = numFont != null && NUMBER_PATTERN.matcher(seg).matches();
                segments.add(new String[]{seg, isNum ? "1" : "0"});
            }
            fixSegmentBoundaries(segments, numFont);
            for (String[] seg : segments) {
                paragraph.add(newText(seg[0], "1".equals(seg[1]) ? numFont : fontCn));
            }
        }
    }

    /**
     * 修正相邻分段边界，使任何元素边界两侧都不是禁排标点：
     * <p>
     * iText 7 在两个相邻 Text 元素之间换行时【不经过】ISplitCharacters 判断，
     * 元素边界是无条件合法的换行点。若边界旁有禁排标点（如段首的"，"、段尾的"）"），
     * 一旦在边界处换行，标点就会出现在行首或行末。
     * 这里通过在两段之间移动字符，把边界挪到两个非标点字符之间；
     * 跨边界移动的字符必须能在目标段字体中渲染（数字段可能不含全角标点字形）。
     */
    private static void fixSegmentBoundaries(List<String[]> segments, PdfFont numFont) {
        int guard = 0;
        boolean changed = true;
        while (changed && guard++ < 10) {
            changed = false;
            for (int i = 0; i < segments.size() - 1; i++) {
                String[] cur = segments.get(i);
                String[] next = segments.get(i + 1);
                if (cur[0].isEmpty() || next[0].isEmpty()) {
                    continue;
                }
                int k = findSafeBoundary(cur[0], next[0], cur[1], next[1], numFont);
                if (k >= 0 && k != cur[0].length()) {
                    String merged = cur[0] + next[0];
                    cur[0] = merged.substring(0, k);
                    next[0] = merged.substring(k);
                    changed = true;
                }
            }
            // 移除被搬空的段；段消失后相邻关系变化，需再迭代一轮
            for (int i = segments.size() - 1; i >= 0; i--) {
                if (segments.get(i)[0].isEmpty()) {
                    segments.remove(i);
                    changed = true;
                }
            }
        }
    }

    /**
     * 在 cur+next 拼接串中就近寻找新的元素边界位置 k（原边界为 cur.length()），要求：
     * <ul>
     * <li>边界两侧的字符都不是禁排标点；</li>
     * <li>被移动到另一段的字符在目标段字体中有字形。</li>
     * </ul>
     * 找不到可行位置时返回 -1（保持原边界，属极窄列等极端情况）。
     */
    private static int findSafeBoundary(String cur, String next, String curType, String nextType, PdfFont numFont) {
        String s = cur + next;
        int len = s.length();
        int k0 = cur.length();
        for (int d = 0; d <= len; d++) {
            if (d == 0) {
                if (isBoundarySafe(s, k0)) {
                    return k0;
                }
                continue;
            }
            int kUp = k0 + d;
            if (kUp <= len && isBoundarySafe(s, kUp)
                    && movedCharsRenderable(s, k0, kUp, curType, nextType, numFont)) {
                return kUp;
            }
            int kDown = k0 - d;
            if (kDown >= 0 && isBoundarySafe(s, kDown)
                    && movedCharsRenderable(s, k0, kDown, curType, nextType, numFont)) {
                return kDown;
            }
        }
        return -1;
    }

    private static boolean isBoundarySafe(String s, int k) {
        if (k > 0 && isPunct(s.charAt(k - 1))) {
            return false;
        }
        return k >= s.length() || !isPunct(s.charAt(k));
    }

    /**
     * 边界从 k0 移到 k 后，跨越边界的字符须能在目标段字体中渲染。
     */
    private static boolean movedCharsRenderable(String s, int k0, int k, String curType, String nextType, PdfFont numFont) {
        if (k > k0) {
            // [k0, k) 从 next 段上移到 cur 段
            for (int i = k0; i < k; i++) {
                if (!typeCanRender(curType, s.charAt(i), numFont)) {
                    return false;
                }
            }
        } else if (k < k0) {
            // [k, k0) 从 cur 段下移到 next 段
            for (int i = k; i < k0; i++) {
                if (!typeCanRender(nextType, s.charAt(i), numFont)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean typeCanRender(String type, char c, PdfFont numFont) {
        if ("0".equals(type)) {
            return true; // 中文段字体支持全角标点与数字
        }
        return numFont != null && numFont.containsGlyph(c);
    }

    private static boolean isPunct(char c) {
        return FORBIDDEN_LINE_START.indexOf(c) != -1 || FORBIDDEN_LINE_END.indexOf(c) != -1;
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
