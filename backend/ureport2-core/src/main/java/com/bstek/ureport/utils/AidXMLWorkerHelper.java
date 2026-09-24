package com.bstek.ureport.utils;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.layout.ElementPropertyContainer;
import com.itextpdf.layout.element.AbstractElement;
import com.itextpdf.layout.element.IElement;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.OverflowPropertyValue;
import com.itextpdf.layout.properties.Property;
import com.itextpdf.layout.properties.UnitValue;

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
                .replace("\t", "")
                .replace(" ", "\u00a0 \u00a0 ")
                .replace("&nbsp;", "\u00a0 \u00a0 ")
                .replace("&emsp;", "\u00a0 \u00a0 ");
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
        if (cssFontFamily != null && cssFontFamily.length() > 0) {
            styleBuilder.append("font-family:").append(cssFontFamily).append(";");
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
                // iText 7 默认 TextRenderer.trimFirst() 会把每个 Text 的行首空白整段丢弃，
                // 导致 HTML 单元格内手工写的首行缩进/前置空格被吞掉。
                // 递归把解析结果里所有 Text 元素的渲染器换成不裁剪行首空白的 NoTrimTextRenderer，
                // 与 DigitalFontUtil 处理纯文本单元格的策略保持一致。
                applyNoTrimTextRenderer(element);
            }
            // 把 html2pdf 的若干顶层元素再包一层 Div，作为整体在 cell 里的容器：
            //   1) 多个 HTML 元素（div/p/span/img 等）共享同一外壳，便于一次性加背景/边框；
            //   2) height:100% 在 cell 有 setHeight 或在 PdfProducer 中按 absolute 兄弟
            //      强制设置后能正确解析，把整段富文本填到 cell 实际高度；
            //   3) overflow:hidden 在 cell 高度小于富文本自然高度时裁切多余内容，
            //      避免溢出覆盖相邻 cell（与 UReport 设计器中"固定行高"行为一致）。
            // 单元素/空列表也照样包一层，简化调用方判断。
            return wrapInFullHeightDiv(elements);
        } catch (Exception e) {
            return new ArrayList<IElement>();
        }
    }

    /**
     * 把 html2pdf 解析出的元素包进一个 height:100% / overflow:hidden 的 Div。
     * <p>
     * 空列表直接返回空（不包空 div，避免 cell 多一个无意义节点）；非空列表全部包一层，
     * 已有的 position:absolute 子块仍然按各自 Property.POSITION 渲染——外层 Div 只控制整体高度/溢出。
     */
    private static List<IElement> wrapInFullHeightDiv(List<IElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return elements == null ? new ArrayList<IElement>() : elements;
        }
        com.itextpdf.layout.element.Div wrapper = new com.itextpdf.layout.element.Div();
        wrapper.setProperty(Property.HEIGHT, UnitValue.createPercentValue(100f));
        wrapper.setProperty(Property.OVERFLOW_X, OverflowPropertyValue.HIDDEN);
        wrapper.setProperty(Property.OVERFLOW_Y, OverflowPropertyValue.HIDDEN);
        for (IElement e : elements) {
            if (e instanceof com.itextpdf.layout.element.IBlockElement) {
                wrapper.add((com.itextpdf.layout.element.IBlockElement) e);
            } else if (e instanceof Image) {
                wrapper.add((Image) e);
            }
        }
        List<IElement> wrapped = new ArrayList<IElement>(1);
        wrapped.add(wrapper);
        return wrapped;
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
     * 递归把 HTML 解析结果里所有 Text 元素的渲染器换成 NoTrimTextRenderer，
     * 保留每个 Text 的行首空白（不被 trimFirst() 丢弃）。
     */
    private static void applyNoTrimTextRenderer(IElement element) {
        if (element instanceof Text) {
            Text text = (Text) element;
            text.setNextRenderer(new DigitalFontUtil.NoTrimTextRenderer(text));
        }
        if (element instanceof AbstractElement) {
            List<IElement> children = ((AbstractElement<?>) element).getChildren();
            if (children != null) {
                for (IElement child : children) {
                    applyNoTrimTextRenderer(child);
                }
            }
        }
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
