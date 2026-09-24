/*******************************************************************************
* Copyright 2017 Bstek
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License.  You may obtain a copy
* of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
* License for the specific language governing permissions and limitations under
* the License.
******************************************************************************/
package com.bstek.ureport.export.pdf;

import com.bstek.ureport.definition.CellStyle;
import com.bstek.ureport.export.pdf.font.FontBuilder;
import com.bstek.ureport.model.Cell;
import com.bstek.ureport.utils.DigitalFontUtil;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.layout.element.Paragraph;
import org.apache.commons.lang.StringUtils;

/**
 * iText 7 中不再使用 Phrase 概念，构建 Paragraph + 多个 Text 来实现。
 * 该类保留为工厂：buildParagraph 创建带样式与混合字体的 Paragraph。
 */
public class CellPhrase {

    public CellPhrase() {
    }

    /**
     * 创建包含单元格内容的 Paragraph，并应用字体/颜色样式。
     */
    public Paragraph buildParagraph(Cell cell, Object cellData) {
        cell.setFillBlankRows(true);
        ResolvedStyle rs = resolveStyle(cell);
        PdfFont font = FontBuilder.getFont(rs.fontName, rs.fontSize, rs.bold, rs.italic, rs.underline);
        Paragraph paragraph = new Paragraph();
        // iText 7 中字号/粗体/斜体/下划线是元素属性而非字体属性，必须显式设置才会生效
        paragraph.setFontSize(rs.fontSize);
        if (rs.bold) {
            paragraph.setBold();
        }
        if (rs.italic) {
            paragraph.setItalic();
        }
        if (rs.underline) {
            paragraph.setUnderline();
        }
        applyColor(paragraph, font, rs);
        String text = cellData == null ? "" : cellData.toString();
        // 数字使用 Times New Roman，其余使用中文字体
        appendMixedText(paragraph, text, font);
        return paragraph;
    }

    private void appendMixedText(Paragraph paragraph, String text, PdfFont fontCn) {
        DigitalFontUtil.appendMixedText(paragraph, text, fontCn);
    }

    private void applyColor(Paragraph paragraph, PdfFont font, ResolvedStyle rs) {
        if (StringUtils.isNotEmpty(rs.forecolor)) {
            String[] color = rs.forecolor.split(",");
            Color c = new DeviceRgb(
                    Integer.valueOf(color[0]),
                    Integer.valueOf(color[1]),
                    Integer.valueOf(color[2]));
            paragraph.setFontColor(c);
        }
        paragraph.setFont(font);
    }


    /**
     * 合并单元格自身、自定义、行、列四层样式，得到最终生效的字体样式。
     */
    public ResolvedStyle resolveStyle(Cell cell) {
        CellStyle style = cell.getCellStyle();
        CellStyle customStyle = cell.getCustomCellStyle();
        CellStyle rowStyle = cell.getRow().getCustomCellStyle();
        CellStyle colStyle = cell.getColumn().getCustomCellStyle();
        String fontName = style.getFontFamily();
        if (customStyle != null && StringUtils.isNotBlank(customStyle.getFontFamily())) {
            fontName = customStyle.getFontFamily();
        }
        if (rowStyle != null && StringUtils.isNotBlank(rowStyle.getFontFamily())) {
            fontName = rowStyle.getFontFamily();
        }
        if (colStyle != null && StringUtils.isNotBlank(colStyle.getFontFamily())) {
            fontName = colStyle.getFontFamily();
        }
        int fontSize = style.getFontSize();
        Boolean bold = style.getBold(), italic = style.getItalic(), underline = style.getUnderline();
        if (customStyle != null) {
            if (customStyle.getBold() != null) {
                bold = customStyle.getBold();
            }
            if (customStyle.getItalic() != null) {
                italic = customStyle.getItalic();
            }
            if (customStyle.getUnderline() != null) {
                underline = customStyle.getUnderline();
            }
            if (customStyle.getFontSize() > 0) {
                fontSize = customStyle.getFontSize();
            }
        }
        if (rowStyle != null) {
            if (rowStyle.getBold() != null) {
                bold = rowStyle.getBold();
            }
            if (rowStyle.getItalic() != null) {
                italic = rowStyle.getItalic();
            }
            if (rowStyle.getUnderline() != null) {
                underline = rowStyle.getUnderline();
            }
            if (rowStyle.getFontSize() > 0) {
                fontSize = rowStyle.getFontSize();
            }
        }
        if (colStyle != null) {
            if (colStyle.getBold() != null) {
                bold = colStyle.getBold();
            }
            if (colStyle.getItalic() != null) {
                italic = colStyle.getItalic();
            }
            if (colStyle.getUnderline() != null) {
                underline = colStyle.getUnderline();
            }
            if (colStyle.getFontSize() > 0) {
                fontSize = colStyle.getFontSize();
            }
        }
        // 字体颜色同样按 单元格 -> 自定义 -> 行 -> 列 的顺序生效
        String forecolor = style.getForecolor();
        if (customStyle != null && StringUtils.isNotBlank(customStyle.getForecolor())) {
            forecolor = customStyle.getForecolor();
        }
        if (rowStyle != null && StringUtils.isNotBlank(rowStyle.getForecolor())) {
            forecolor = rowStyle.getForecolor();
        }
        if (colStyle != null && StringUtils.isNotBlank(colStyle.getForecolor())) {
            forecolor = colStyle.getForecolor();
        }
        if (bold == null) bold = false;
        if (italic == null) italic = false;
        if (underline == null) underline = false;
        if (StringUtils.isBlank(fontName)) {
            fontName = "宋体";
        }
        if (fontSize <= 0) {
            fontSize = 12;
        }
        return new ResolvedStyle(fontName, fontSize, bold, italic, underline, forecolor);
    }

    public static class ResolvedStyle {
        public final String fontName;
        public final int fontSize;
        public final boolean bold;
        public final boolean italic;
        public final boolean underline;
        public final String forecolor;

        ResolvedStyle(String fontName, int fontSize, boolean bold, boolean italic, boolean underline, String forecolor) {
            this.fontName = fontName;
            this.fontSize = fontSize;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.forecolor = forecolor;
        }
    }
}