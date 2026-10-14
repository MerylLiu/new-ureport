package com.bstek.ureport.utils;

import com.bstek.ureport.export.pdf.font.FontBuilder;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.ElementPropertyContainer;
import com.itextpdf.layout.element.AbstractElement;
import com.itextpdf.layout.element.IElement;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AidXMLWorkerHelper {

    /**
     * 匹配 CSS 中字体相关的声明（font-family/font-size/font-weight/font-style/
     * text-decoration/color，含结尾分号），用于移除 HTML 自带的字体样式。
     * <p>
     * 前置断言 (?<![\w-]) 用于排除 background-color 之类的复合属性名。
     */
    private static final String FONT_STYLE_DECL_PATTERN =
            "(?i)(?<![\\w-])(font-family|font-size|font-weight|font-style|text-decoration|color)\\s*:\\s*[^;\"'}]+;?";

    /**
     * 将 HTML 解析为 iText 7 的元素列表，单元格（表格）的字体样式优先于 HTML 自带样式。
     * <p>
     * iText 5 时代 AidXMLWorkerHelper.AidFontsProvider 始终返回单元格的 Font，
     * 因此 HTML 的字体族/字号/粗细/斜体/下划线/颜色都不会生效，一律以单元格设置为准；
     * 迁移到 iText 7 后需要显式还原该行为，否则 HTML 单元格的字体样式与表格设置不一致。
     *
     * @param bold      单元格是否加粗
     * @param italic    单元格是否斜体
     * @param underline 单元格是否下划线
     * @param forecolor 单元格字体颜色，格式 r,g,b
     */
    public static List<IElement> parseToElementList(String html, String css, PdfFont font, String fontFamily,
                                                    float fontSize, boolean bold, boolean italic,
                                                    boolean underline, String forecolor) {
        // 预处理：清洗 HTML 标签并替换 em 单位
        html = html.replace("<br>", "").replace("<hr>", "")
                .replace("<img>", "").replace("<param>", "")
                .replace("<link>", "").replace("></", "> </")
                .replace("\n", "")
                .replace("\t", "");
        // 空格类字符的换算（实测 html2pdf 4.0.5 行为）：
        // - NBSP(U+00A0) 不折叠、不裁剪，行首/行中/行尾/连续均 1:1 保留；
        // - em 空格(U+2003)、全角空格(U+3000)、&emsp;/&ensp; 在行首会被裁剪、
        //   行中被规整为可折叠的普通空格，原样交给渲染器会"消失"。
        // 统一先换成 NBSP 计数：&nbsp;/&ensp; → 1 个；em/全角/&emsp;（1em）→ 2 个。
        // 普通空格不做替换：交给 CSS white-space:normal 按标准折叠/裁剪，
        // HTML 源码的标签间缩进空格保持不可见，与浏览器一致。
        html = html.replace(" ", "\u00a0\u00a0")
                .replace("　", "\u00a0\u00a0")
                .replace("&nbsp;", "\u00a0")
                .replace("&ensp;", "\u00a0")
                .replace("&emsp;", "\u00a0\u00a0");

        // NBSP 的落地方式按单元格字体是否含 U+00A0 字形分两路：         // 1) 含字形（宋体/微软雅黑/黑体等主流字体）：NBSP 与正文同字体、同一条文本 run，         //    任何位置（含行首）+ justify + 折行都安全，宽度恰好等于该字体的空格宽         //    （CJK 字体半宽空格 = 0.5em，2 个 NBSP = 1em，与全角空格等宽）。这也是对         //    原 bug 的修复：原代码把 1 个 &nbsp; 替换成 "NBSP+空格+NBSP+空格" 4 个字符宽，         //    富文本编辑器用 &nbsp; 做首行缩进时，PDF 首行空格比实际多 4 倍。         // 2) 缺字形（如未注册字体名回退的 STSong-Light）：NBSP 会被 FontSelector 拆到         //    回退字体形成独立 run；行首分 run + text-align:justify + 折行的组合会触发         //    html2pdf 的布局缺陷（后续文本块被移到 x=0 即单元格之外）。因此：         //    - 文本节点开头的连续 NBSP（首行缩进）→ 定宽 inline-block 空 span（无文本 run，         //      实测行首安全、宽度精确）；         //    - 其余（行中）NBSP → 普通空格（按 HTML 规则折叠，避免回退字体 run）。
        if (!fontContainsNbsp(font)) {
            html = replaceLeadingNbspWithSpacer(html, font, fontSize);
            html = html.replace("\u00a0", " ");
        }

        Pattern emPattern = Pattern.compile("(\\d*\\.?\\d+)em");
        Matcher emMatcher = emPattern.matcher(html);
        float baseSize = fontSize > 0 ? fontSize : 12f;
        StringBuffer emBuf = new StringBuffer();
        while (emMatcher.find()) {
            // CSS 标准 1em = 当前字号（fontSize 单位已是 pt，直接相乘）。
            // 原 iText 5 公式 (em*fontSize+8)*0.75 沿自 XMLWorker 的 px 口径（+8px 再按 0.75 折 pt），
            // 只在 2em@12pt 等个别组合与标准一致，其余组合偏差达 0.75 字（如 4em@24pt 偏 18pt）。
            float value = Float.parseFloat(emMatcher.group(1)) * baseSize;
            emMatcher.appendReplacement(emBuf,
                    Matcher.quoteReplacement(emToPt(value)));
        }
        emMatcher.appendTail(emBuf);
        html = emBuf.toString();

        // 把文本节点里的连续数字串包到 span 强制 Times New Roman。
        // 仅在标签外/实体外处理，避免破坏 HTML 结构。比 CSS font-family 列表前项更可靠：
        // 能覆盖父级、内联样式（如 <div style="font-family:SimSun">）对字体的覆盖。
        html = wrapDigitsWithTimesNewRoman(html);

        ConverterProperties props = new ConverterProperties();
        DefaultFontProvider fontProvider = new DefaultFontProvider(true, true, true);
        String cssFontFamily = null;
        if (font != null) {
            try {
                // 注册单元格字体程序，使 CSS font-family 能解析到该字体（含 classpath TTC 注册的字体）
                FontProgram fontProgram = font.getFontProgram();
                if (fontProgram != null) {
                    fontProvider.addFont(fontProgram);
                    // CSS 中的非 ASCII 字体名（如"宋体"）无法被 styled-xml-parser 解析，
                    // 因此从字体程序中提取 ASCII 家族名（如 SimSun）用于 CSS
                    cssFontFamily = extractAsciiFamilyNames(fontProgram);
                }
            } catch (Exception ignore) {
                // 注册失败时回退到默认字体
            }
        }
        if (cssFontFamily == null && fontFamily != null && isAscii(fontFamily)) {
            cssFontFamily = fontFamily.trim();
        }
        // 注册 Times-Roman 作为数字字体：built-in PDF 标准字体，视觉等同 Times New Roman。
        // 别名 "TimesNewRoman" 暴露给 CSS，iText 的 FontSelector 按字符自动挑选字体——
        // 数字落在 TimesNewRoman，缺字形（如中文）则 fall-through 到下一项（单元格字体）。
        try {
            PdfFont timesFont = FontBuilder.getFont("Times New Roman", font.getPdfObject().size(), false, false, false);
            fontProvider.addFont(timesFont.getFontProgram(), "Identity-H");
        } catch (Exception ignore) {
            // 注册失败时数字回退到单元格字体
        }
        props.setFontProvider(fontProvider);
        props.setCharset("UTF-8");

        // 单元格字体样式：注入在用户 css 之前。字体族可能解析不到 ASCII 名而缺失，
        // 但字号必须无条件注入，否则 HTML 会按 html2pdf 的默认字号（12pt）渲染。
        // 注意：粗体/斜体/下划线不在这里注入——CSS 的 font-weight/font-style 只会让
        // html2pdf 设置 FONT_WEIGHT/FONT_STYLE 属性，由 FontSelector 去挑选粗体/斜体
        // 字体文件；当字体本身没有对应的粗斜体 face（如宋体）时完全不会生效。
        // 因此这三项改为在解析完成后直接设置到元素属性上（见 applyCellFontStyle），
        // 由 iText 7 用文本渲染模式/倾斜矩阵/下划线来自动模拟，效果与 iText 5 一致。
        StringBuilder styleBuilder = new StringBuilder();
        styleBuilder.append("body,div,p,span,td,th{padding:0;margin:0;");
        // 数字优先使用 Times New Roman（CSS font-family 前项优先 + 缺字形自动 fall-through）
        if (cssFontFamily != null && cssFontFamily.length() > 0) {
            styleBuilder.append("font-family:'TimesNewRoman',").append(cssFontFamily).append(";");
        } else {
            styleBuilder.append("font-family:'TimesNewRoman';");
        }
        if (fontSize > 0) {
            styleBuilder.append("font-size:").append(fontSize).append("pt;");
        }
        String rgb = parseRgb(forecolor);
        if (rgb != null) {
            styleBuilder.append("color:rgb(").append(rgb).append(");");
        }
        styleBuilder.append("}");
        styleBuilder.append(css == null ? "" : css);
        String fullHtml = "<html><head><style>" + styleBuilder + "</style></head><body>" + html + "</body></html>";

        try {
            List<IElement> elements = HtmlConverter.convertToElements(fullHtml, props);
            if (elements == null) {
                return new ArrayList<IElement>();
            }
            for (IElement element : elements) {
                if (bold || italic || underline) {
                    applyCellFontStyle(element, bold, italic, underline);
                }
            }
            return elements;
        } catch (Exception e) {
            return new ArrayList<IElement>();
        }
    }

    /**
     * 递归给 HTML 解析出的元素设置单元格的粗体/斜体/下划线。
     * iText 7 中这三项是元素属性（TextRenderer 会据此模拟粗体/斜体并绘制下划线），
     * 只有落到每个文本元素上才会生效，因此需要连同子元素一起处理。
     */
    private static void applyCellFontStyle(IElement element, boolean bold, boolean italic, boolean underline) {
        if (element instanceof ElementPropertyContainer) {
            ElementPropertyContainer<?> container = (ElementPropertyContainer<?>) element;
            if (bold) {
                container.setBold();
            }
            if (italic) {
                container.setItalic();
            }
            if (underline) {
                container.setUnderline();
            }
        }
        if (element instanceof AbstractElement) {
            List<IElement> children = ((AbstractElement<?>) element).getChildren();
            if (children != null) {
                for (IElement child : children) {
                    applyCellFontStyle(child, bold, italic, underline);
                }
            }
        }
    }

    /**
     * 单元格字体是否包含 NBSP(U+00A0) 字形。
     * 宋体/微软雅黑/黑体/楷体等主流 CJK 字体均包含；
     * 未注册字体名回退出的 STSong-Light 不包含。
     */
    private static boolean fontContainsNbsp(PdfFont font) {
        if (font == null) {
            return false;
        }
        try {
            return font.containsGlyph('\u00a0');
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * 把文本节点开头（html 串首，或紧跟某个 "&gt;" 之后）的连续 NBSP
     * 换成定宽 inline-block 空 span。
     * <p>
     * 仅用于字体缺 NBSP 字形的回退场景：行首 NBSP 会被拆到回退字体形成独立文本 run，
     * 与 justify + 折行组合会触发 html2pdf 的布局缺陷。空 span 无文本 run，
     * 实测行首安全且宽度精确。宽度按该字体空格宽度 × 个数计算。
     */
    private static String replaceLeadingNbspWithSpacer(String html, PdfFont font, float fontSize) {
        float base = fontSize > 0 ? fontSize : 12f;
        float halfWidth = base / 2f;
        if (font != null) {
            try {
                float w = font.getWidth(' ', base);
                if (w > 0) {
                    halfWidth = w;
                }
            } catch (Exception ignore) {
                // 度量失败时按半宽估算
            }
        }
        Pattern pattern = Pattern.compile("(^|>)\\u00a0+");
        Matcher matcher = pattern.matcher(html);
        StringBuffer buf = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            int count = matcher.group().length() - prefix.length();
            String spacer = "<span style=\"display:inline-block;width:"
                    + formatPt(count * halfWidth) + "pt\"></span>";
            matcher.appendReplacement(buf, Matcher.quoteReplacement(prefix + spacer));
        }
        matcher.appendTail(buf);
        return buf.toString();
    }

    /**
     * 把 HTML 文本节点（标签外、实体外）的连续数字串包到 span，强制使用 Times New Roman。
     * <p>
     * 仅在文本内容中处理：
     * <ul>
     *   <li>标签属性里的数字不处理（属性不渲染）；</li>
     *   <li>HTML 实体（如 {@code &#48;} / {@code &}）里的数字不处理（保留实体语法，
     *       由 iText 解析为字符后再走 font-family 选择）。</li>
     * </ul>
     * 连续数字合并到同一个 span，避免 "123" 拆成三个 span 触发额外的文本 run。
     * <p>
     * 比 CSS font-family 列表前项更可靠：能覆盖父级/兄弟/内联样式（如
     * {@code <div style="font-family:SimSun">123</div>}）对字体的覆盖，是保证
     * "所有数字均落在 Times New Roman"的唯一稳妥方式。
     */
    private static String wrapDigitsWithTimesNewRoman(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        StringBuilder result = new StringBuilder(html.length() + 32);
        int len = html.length();
        int i = 0;
        boolean inTag = false;
        boolean inEntity = false;
        while (i < len) {
            char c = html.charAt(i);
            if (inTag) {
                result.append(c);
                if (c == '>') {
                    inTag = false;
                }
                i++;
            } else if (inEntity) {
                result.append(c);
                // 实体结束条件：';' 或意外字符（'<' 开启新标签、空格中断）
                if (c == ';' || c == '<' || c == ' ') {
                    inEntity = false;
                }
                i++;
            } else if (c == '<') {
                inTag = true;
                result.append(c);
                i++;
            } else if (c == '&') {
                inEntity = true;
                result.append(c);
                i++;
            } else if (c >= '0' && c <= '9') {
                int start = i;
                while (i < len && html.charAt(i) >= '0' && html.charAt(i) <= '9') {
                    i++;
                }
                result.append("<span style=\"font-family:'TimesNewRoman'\">")
                        .append(html, start, i)
                        .append("</span>");
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 把 pt 数值格式化为 CSS 字面量，整数不带小数点，小数最多保留两位。
     */
    private static String formatPt(float value) {
        double v = value;
        double rounded = Math.round(v);
        if (Math.abs(v - rounded) < 0.005) {
            return (long) rounded + "";
        }
        long cents = Math.round(v * 100.0);
        return String.valueOf(cents / 100.0);
    }

    /**
     * 把 em 换算出的 pt 数值格式化为 CSS 字面量，整数不带小数点。
     * 用 double 运算：float 在 x.xx5 边界会因表示误差向错误方向舍入
     * （如 2.675f 实际是 2.6749999，*100 后 round 掉成 2.67）。
     */
    private static String emToPt(float value) {
        double v = value;
        double rounded = Math.round(v);
        if (Math.abs(v - rounded) < 0.01) {
            return (long) rounded + "pt";
        }
        // 先取整到百分位再输出，避免 double 直接打印带科学计数尾随位
        long cents = Math.round(v * 100.0);
        return (cents / 100.0) + "pt";
    }

    /**
     * 把单元格的 r,g,b 颜色转换为 CSS rgb() 的参数形式，非法值返回 null。
     */
    private static String parseRgb(String forecolor) {
        if (forecolor == null || forecolor.length() == 0) {
            return null;
        }
        String[] rgb = forecolor.split(",");
        if (rgb.length < 3) {
            return null;
        }
        return rgb[0].trim() + "," + rgb[1].trim() + "," + rgb[2].trim();
    }

    /**
     * 从字体程序中提取 ASCII 家族名，组成 CSS font-family 值（如 'SimSun'）。
     * 返回 null 表示没有可用的 ASCII 名。
     */
    private static String extractAsciiFamilyNames(FontProgram fontProgram) {
        String[][] familyNames = null;
        try {
            familyNames = fontProgram.getFontNames().getFamilyName();
        } catch (Exception ignore) {
            return null;
        }
        if (familyNames == null || familyNames.length == 0) {
            return null;
        }
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<String>();
        for (String[] row : familyNames) {
            if (row == null || row.length == 0) {
                continue;
            }
            String name = row[row.length - 1];
            if (name != null && name.length() > 0 && isAscii(name)) {
                names.add("'" + name + "'");
            }
        }
        return names.isEmpty() ? null : String.join(",", names);
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }
}
