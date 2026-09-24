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

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.bstek.ureport.builder.Context;
import com.bstek.ureport.builder.paging.HeaderFooter;
import com.bstek.ureport.builder.paging.Page;
import com.bstek.ureport.definition.HeaderFooterDefinition;
import com.bstek.ureport.definition.Orientation;
import com.bstek.ureport.definition.Paper;
import com.bstek.ureport.exception.ReportComputeException;
import com.bstek.ureport.export.pdf.font.FontBuilder;
import com.bstek.ureport.model.Report;
import com.bstek.ureport.utils.DigitalFontUtil;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;

/**
 * iText 7 的页面事件机制（IEventHandler + PdfDocumentEvent）。
 */
public class PageHeaderFooterEvent implements IEventHandler {

    private Report report;

    public PageHeaderFooterEvent(Report report) {
        this.report = report;
    }

    @Override
    public void handleEvent(Event currentEvent) {
        PdfDocumentEvent docEvent = (PdfDocumentEvent) currentEvent;
        PdfDocument pdfDoc = docEvent.getDocument();
        PdfPage page = docEvent.getPage();
        int pageNumber = pdfDoc.getPageNumber(page);
        List<Page> pages = report.getPages();
        HeaderFooter header;
        HeaderFooter footer;
        if (pageNumber <= pages.size()) {
            Page p = pages.get(pageNumber - 1);
            header = p.getHeader();
            footer = p.getFooter();
        } else {
            // 报表模型里没有对应的页，有两种情况：
            // 1) 数据集没有数据，report.getPages() 为空，iText 仍会写出一张页面（原来的纯空白页）；
            // 2) 表格内容超出可用高度，被切分到模型页数之外多出来的页面。
            // 这些页面拿不到 Page 上的页眉/页脚对象，若直接 return 就会是一张连页眉页脚都没有的空白页，
            // 这里退回到报表自身的页眉/页脚定义来构建，保证页面上有页眉页脚。
            header = buildExtraPageHeaderFooter(report.getHeader(), pageNumber);
            footer = buildExtraPageHeaderFooter(report.getFooter(), pageNumber);
        }
        if (header != null) {
            buildTable(pdfDoc, page, header, true);
        }
        if (footer != null) {
            buildTable(pdfDoc, page, footer, false);
        }
    }

    /**
     * 为模型页数之外的页面构建页眉/页脚。
     * 页眉/页脚的文字来自定义里的表达式，需要借助上下文求值；求值失败时返回 null，
     * 页面保持原来的无页眉页脚状态，不影响导出。
     */
    private HeaderFooter buildExtraPageHeaderFooter(HeaderFooterDefinition definition, int pageNumber) {
        Context context = report.getContext();
        if (definition == null || context == null) {
            return null;
        }
        int pageIndex = context.getPageIndex();
        try {
            return definition.buildHeaderFooter(pageNumber, context);
        } catch (Exception ex) {
            return null;
        } finally {
            // buildHeaderFooter 会改写上下文中的页码，这里还原，避免影响同一 Report 上的后续导出
            context.setPageIndex(pageIndex);
        }
    }

    private void buildTable(PdfDocument pdfDoc, PdfPage page, HeaderFooter hf, boolean header) {
        Paper paper = report.getPaper();
        int width = paper.getWidth();
        if (paper.getOrientation().equals(Orientation.landscape)) {
            width = paper.getHeight();
        }
        int leftMargin = paper.getLeftMargin();
        int rightMargin = paper.getRightMargin();
        int tableWidth = width - leftMargin - rightMargin;
        int height = paper.getHeight();
        if (paper.getOrientation().equals(Orientation.landscape)) {
            height = paper.getWidth();
        }
        int margin = hf.getMargin();
        int hfHeight = hf.getHeight();
        String left = hf.getLeft();
        String center = hf.getCenter();
        String right = hf.getRight();
        String pageText = "";
        if (right != null && (right.contains("|page|") || right.contains("|pageLast|"))) {
            if (right.contains("|page|")) {
                String[] split = right.split("\\|page\\|");
                right = split[0];
                pageText = split[1];
            }
            if (right.contains("|pageLast|")) {
                String[] split = right.split("\\|pageLast\\|");
                String rightText = split[1];
                String[] split1 = rightText.split("\\$");
                pageText = split1[0];
                String count = split1[1];
                if (Integer.parseInt(pageText.replace("-", "")) < Integer.parseInt(count) || Integer.parseInt(count) == 1) {
                    left = "";
                    center = "";
                    right = "";
                    pageText = "";
                } else {
                    right = split[0];
                }
            }
        }
        try {
            float pageLeft = page.getPageSize().getLeft();
            float pageRight = page.getPageSize().getRight();
            float yTop = header ? (height - margin) : (margin + hfHeight);
            float yBottom = header ? (height - margin - hfHeight) : margin;

            boolean hasText = StringUtils.isNotEmpty(left) || StringUtils.isNotEmpty(center)
                    || StringUtils.isNotEmpty(right) || StringUtils.isNotEmpty(pageText);
            if (!hasText) {
                return;
            }

            // 字体：iText 7 的粗体/斜体/下划线是元素属性而非字体属性，
            // 必须交给布局引擎（Paragraph + Canvas）绘制才会生效
            PdfFont font = FontBuilder.getFont(hf.getFontFamily(), hf.getFontSize(), hf.isBold(), hf.isItalic(), hf.isUnderline());
            Color fontColor = new DeviceRgb(0, 0, 0);
            String fc = hf.getForecolor();
            if (StringUtils.isNotEmpty(fc)) {
                String[] c = fc.split(",");
                fontColor = new DeviceRgb(
                        Integer.valueOf(c[0]),
                        Integer.valueOf(c[1]),
                        Integer.valueOf(c[2]));
            }

            // 与 iText 5 一致：页眉/页脚单元格上下都有边框线（Rectangle.TOP | Rectangle.BOTTOM），
            // 线宽取 iText 5 PdfPCell 的默认边框宽度 0.5
            PdfCanvas lineCanvas = new PdfCanvas(page);
            lineCanvas.setLineWidth(0.5f);
            lineCanvas.setStrokeColor(new DeviceRgb(0, 0, 0));
            lineCanvas.moveTo(pageLeft + leftMargin, yBottom);
            lineCanvas.lineTo(pageRight - rightMargin, yBottom);
            lineCanvas.stroke();
            lineCanvas.moveTo(pageLeft + leftMargin, yTop);
            lineCanvas.lineTo(pageRight - rightMargin, yTop);
            lineCanvas.stroke();
            lineCanvas.release();

            // iText 5 中单元格 padding 为 5，文字在行内垂直居中
            float padding = 5f;
            float textY = (yTop + yBottom) / 2f;
            if (StringUtils.isNotEmpty(left)) {
                drawText(page, hf, font, fontColor, left, pageLeft + leftMargin + padding, textY,
                        TextAlignment.LEFT);
            }
            if (StringUtils.isNotEmpty(center)) {
                drawText(page, hf, font, fontColor, center, (pageLeft + pageRight) / 2f, textY,
                        TextAlignment.CENTER);
            }
            if (StringUtils.isNotEmpty(right)) {
                drawText(page, hf, font, fontColor, right, pageRight - rightMargin - padding, textY,
                        TextAlignment.RIGHT);
            }
            // 与 iText 5 一致：页码占位符（|page|/|pageLast|）拆分出的后一段文字在下一行单独绘制，
            // 该行无边框，垂直方向居中于页眉/页脚区域的下一行内
            if (StringUtils.isNotEmpty(pageText)) {
                drawText(page, hf, font, fontColor, pageText, pageRight - rightMargin - padding,
                        yBottom - hfHeight / 2f, TextAlignment.RIGHT);
            }
        } catch (RuntimeException de) {
            throw new ReportComputeException(de);
        }
    }

    /**
     * 用布局引擎在指定位置绘制一段带样式的页眉/页脚文字。
     * 直接操作 PdfCanvas 无法表达粗体/斜体/下划线，只有走 Paragraph 才与正文样式一致。
     */
    private void drawText(PdfPage page, HeaderFooter hf, PdfFont font, Color fontColor,
                          String text, float x, float y, TextAlignment align) {
        Paragraph paragraph = new Paragraph()
                .setFont(font)
                .setFontSize(hf.getFontSize())
                .setFontColor(fontColor);
        // 用 Text 承载文字并换用不裁剪行首空白的渲染器，否则页眉/页脚里靠空格做的缩进会被丢掉
        DigitalFontUtil.appendPlainText(paragraph, text, font);
        if (hf.isBold()) {
            paragraph.setBold();
        }
        if (hf.isItalic()) {
            paragraph.setItalic();
        }
        if (hf.isUnderline()) {
            paragraph.setUnderline();
        }
        Canvas canvas = new Canvas(page, page.getPageSize());
        canvas.showTextAligned(paragraph, x, y, align, VerticalAlignment.MIDDLE);
        canvas.close();
    }
}