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

import com.bstek.ureport.ChineseSplitCharacter;
import com.bstek.ureport.builder.paging.Page;
import com.bstek.ureport.chart.ChartData;
import com.bstek.ureport.definition.Alignment;
import com.bstek.ureport.definition.CellStyle;
import com.bstek.ureport.definition.Orientation;
import com.bstek.ureport.definition.Paper;
import com.bstek.ureport.exception.ReportComputeException;
import com.bstek.ureport.export.FullPageData;
import com.bstek.ureport.export.PageBuilder;
import com.bstek.ureport.export.Producer;
import com.bstek.ureport.model.Column;
import com.bstek.ureport.model.Image;
import com.bstek.ureport.model.Report;
import com.bstek.ureport.model.Row;
import com.bstek.ureport.utils.AidXMLWorkerHelper;
import com.bstek.ureport.utils.ImageUtils;
import com.bstek.ureport.utils.UnitUtils;
import com.bstek.ureport.export.pdf.font.FontBuilder;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.IElement;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.layout.LayoutArea;
import com.itextpdf.layout.layout.LayoutContext;
import com.itextpdf.layout.layout.LayoutResult;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.Property;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.layout.renderer.DocumentRenderer;
import com.itextpdf.layout.renderer.IRenderer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * @since 2017年3月10日
 */
public class PdfProducer implements Producer {
    /**
     * 实测单元格内容高度时使用的布局高度上限，只要足够大即可（内容会被完整排版出来）。
     */
    private static final float CONTENT_MEASURE_MAX_HEIGHT = 10000f;

    @Override
    public void produce(Report report, OutputStream outputStream) {
        Paper paper = report.getPaper();
        float width = paper.getWidth();
        float height = paper.getHeight();
        Rectangle pageSize = new Rectangle(width, height);
        if (paper.getOrientation().equals(Orientation.landscape)) {
            pageSize = new Rectangle(height, width);
        }
        float leftMargin = paper.getLeftMargin();
        float rightMargin = paper.getRightMargin();
        float topMargin = paper.getTopMargin();
        float bottomMargin = paper.getBottomMargin();

        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        PageHeaderFooterEvent headerFooterEvent = new PageHeaderFooterEvent(report);
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, headerFooterEvent);
        Document document = new Document(pdfDoc, new PageSize(pageSize));
        document.setMargins(topMargin, rightMargin, bottomMargin, leftMargin);

        try {
            List<Column> columns = report.getColumns();
            List<Integer> columnsWidthList = new ArrayList<Integer>();
            int[] intArr = buildColumnSizeAndTotalWidth(columns, columnsWidthList);
            int totalWidth = intArr[1];
            int[] columnsWidth = new int[columnsWidthList.size()];
            for (int i = 0; i < columnsWidthList.size(); i++) {
                columnsWidth[i] = columnsWidthList.get(i);
            }
            FullPageData pageData = PageBuilder.buildFullPageData(report);
            List<List<Page>> list = pageData.getPageList();
            Map<Row, Map<Column, com.bstek.ureport.model.Cell>> cellMap = report.getRowColCellMap();
            if (list.size() > 0) {
                renderMultiColumnPages(document, list, columns, columnsWidth, totalWidth, paper, cellMap);
            } else {
                List<Page> pages = report.getPages();
                int pageCount = pages.size();
                for (int idx = 0; idx < pageCount; idx++) {
                    Page p = pages.get(idx);
                    Table table = buildPageTable(columnsWidth, totalWidth);
                    fillChildTable(table, p, columns, cellMap, document, buildUsablePageHeight(paper));
                    document.add(table);
                    // 仅在最后一页之外追加分页符，避免出现空白末尾页
//                    if (idx < pageCount - 1) {
//                        document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
//                    }
                }
            }
        } catch (Exception ex) {
            throw new ReportComputeException(ex);
        } finally {
            closeQuietly(document);
            // 清理当前线程的 PdfFont 缓存，避免缓存的字体对象属于已关闭的 PdfDocument
            FontBuilder.clearThreadFontCache();
        }
    }

    /**
     * 渲染多列（横向分页）场景：每条 list 元素是一组按列并排的页面。
     * 表格宽度 = columnCount * totalWidth + (columnCount-1) * columnMargin。
     */
    private void renderMultiColumnPages(Document document,
                                        List<List<Page>> list,
                                        List<Column> columns,
                                        int[] columnsWidth,
                                        int totalWidth,
                                        Paper paper,
                                        Map<Row, Map<Column, com.bstek.ureport.model.Cell>> cellMap) {
        int columnCount = paper.getColumnCount();
        int w = columnCount * totalWidth + (columnCount - 1) * paper.getColumnMargin();
        int size = columnCount + (columnCount - 1);
        float[] widths = new float[size];
        for (int i = 0; i < size; i++) {
            int mode = (i + 1) % 2;
            if (mode == 0) {
                widths[i] = paper.getColumnMargin();
            } else {
                widths[i] = totalWidth;
            }
        }

        int listSize = list.size();
        for (int idx = 0; idx < listSize; idx++) {
            List<Page> pages = list.get(idx);
            Table table = new Table(UnitValue.createPointArray(widths));
            table.setFixedLayout();
            table.setWidth(w);
            // 让外层表格在可用宽度内居左铺开，便于多列并排显示
            table.setHorizontalAlignment(HorizontalAlignment.LEFT);

            int ps = pages.size();
            for (int i = 0; i < ps; i++) {
                if (i > 0) {
                    Cell pdfMarginCell = new Cell();
                    pdfMarginCell.setBorder(Border.NO_BORDER);
                    pdfMarginCell.setPadding(0);
                    table.addCell(pdfMarginCell);
                }
                Page p = pages.get(i);
                Table childTable = buildPageTable(columnsWidth, totalWidth);
                try {
                    fillChildTable(childTable, p, columns, cellMap, document, buildUsablePageHeight(paper));
                } catch (Exception ex) {
                    throw new ReportComputeException(ex);
                }

                Cell pdfContainerCell = new Cell().add(childTable);
                pdfContainerCell.setBorder(Border.NO_BORDER);
                pdfContainerCell.setPadding(0);
                pdfContainerCell.setHorizontalAlignment(HorizontalAlignment.LEFT);
                pdfContainerCell.setVerticalAlignment(VerticalAlignment.TOP);
                table.addCell(pdfContainerCell);
            }
            // 列数未填满时补占位空白 cell，保持列宽整齐
            if (ps < columnCount) {
                int left = columnCount - ps;
                for (int i = 0; i < left; i++) {
                    Cell pdfMarginCell = new Cell();
                    pdfMarginCell.setBorder(Border.NO_BORDER);
                    pdfMarginCell.setPadding(0);
                    table.addCell(pdfMarginCell);
                    if (columnCount > 1) {
                        // 补一个列间距 cell（如果 columnCount>1 才有间距列）
                        Cell pdfGap = new Cell();
                        pdfGap.setBorder(Border.NO_BORDER);
                        pdfGap.setPadding(0);
                        table.addCell(pdfGap);
                    }
                }
            }
            document.add(table);
            // 除最后一页外强制分页
            if (idx < listSize - 1) {
                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            }
        }
    }

    /**
     * 构造一个固定布局的页面子表格（每行的列宽 = columnsWidth）。
     */
    private Table buildPageTable(int[] columnsWidth, int totalWidth) {
        Table table = new Table(UnitValue.createPointArray(toFloat(columnsWidth)));
        table.setFixedLayout();
        table.setWidth(totalWidth);
        table.setHorizontalAlignment(HorizontalAlignment.LEFT);
        return table;
    }

    private static float[] toFloat(int[] ints) {
        float[] fs = new float[ints.length];
        for (int i = 0; i < ints.length; i++) {
            fs[i] = ints[i];
        }
        return fs;
    }

    private void fillChildTable(Table table,
                                Page page,
                                List<Column> columns,
                                Map<Row, Map<Column, com.bstek.ureport.model.Cell>> cellMap,
                                Document document,
                                int usablePageHeight) throws Exception {
        List<Row> rows = page.getRows();
        IRenderer measureRootRenderer = new DocumentRenderer(document);
        // 1) 先构建本页所有单元格（此时不设高度），并对开启了换行计算的行实测内容高度
        List<PdfPageRow> pageRows = new ArrayList<PdfPageRow>();
        for (Row row : rows) {
            Map<Column, com.bstek.ureport.model.Cell> colMap = cellMap.get(row);
            if (colMap == null) {
                continue;
            }
            boolean needMeasure = false;
            for (Column col : columns) {
                com.bstek.ureport.model.Cell cellInfo = colMap.get(col);
                if (cellInfo != null && isWrapCompute(cellInfo)) {
                    needMeasure = true;
                    break;
                }
            }
            PdfPageRow pageRow = new PdfPageRow(row);
            for (Column col : columns) {
                if (col.getWidth() < 1) {
                    continue;
                }
                com.bstek.ureport.model.Cell cellInfo = colMap.get(col);
                if (cellInfo == null) {
                    continue;
                }
                int cellWidth = buildCellWidth(cellInfo, columns);
                Cell pdfcell = buildPdfCell(cellInfo, cellWidth);
                float contentHeight = needMeasure ? measureCellContent(pdfcell, cellWidth, measureRootRenderer) : 0;
                pageRow.cells.add(new PdfPageCell(cellInfo, pdfcell, contentHeight));
            }
            if (!pageRow.cells.isEmpty()) {
                pageRows.add(pageRow);
            }
        }
        // 2) 计算每行高度
        int[] heights = new int[pageRows.size()];
        for (int i = 0; i < pageRows.size(); i++) {
            heights[i] = computeRowHeight(pageRows.get(i));
        }
        // 跨行合并单元格：高度为所跨各行高度之和，实测内容更高时加高其最后一行
        for (int i = 0; i < pageRows.size(); i++) {
            for (PdfPageCell pageCell : pageRows.get(i).cells) {
                int rowSpan = pageCell.cellInfo.getPageRowSpan();
                if (rowSpan <= 1) {
                    continue;
                }
                int end = Math.min(pageRows.size(), i + rowSpan);
                int need = (int) Math.ceil(pageCell.contentHeight);
                int total = sumRowHeights(heights, i, end);
                if (need > total) {
                    heights[end - 1] += need - total;
                }
            }
        }
        // 3) 写入表格：同一行的所有单元格使用同一个高度
        int actualTotalHeight = 0;
        for (int i = 0; i < pageRows.size(); i++) {
            PdfPageRow pageRow = pageRows.get(i);
            actualTotalHeight += heights[i];
            for (PdfPageCell pageCell : pageRow.cells) {
                int rowSpan = pageCell.cellInfo.getPageRowSpan();
                int height = heights[i];
                if (rowSpan > 1) {
                    height = sumRowHeights(heights, i, Math.min(pageRows.size(), i + rowSpan));
                }
                if (height > 0) {
                    pageCell.pdfCell.setMinHeight(height);
                }
                table.addCell(pageCell.pdfCell);
            }
        }
        // 4) 补一行不可见填充行：让本页占用总高度等于模型分页时每页的可用高度。
        // 单元格变矮后如果本页高度不足，下一页的内容会提前挤进本页并被 iText 继续切分，
        // 导致物理分页与模型分页错位（页眉页脚、页码都会对不上）；
        // 本页行数不为空、且实测总高度小于可用高度时才需要补。
        int fillerHeight = usablePageHeight - actualTotalHeight;
        int columnCount = 0;
        for (Column col : columns) {
            if (col.getWidth() > 0) {
                columnCount++;
            }
        }
        if (!pageRows.isEmpty() && fillerHeight > 0 && columnCount > 0
                && isRowCellCountMatched(pageRows, columnCount)) {
            Cell fillerCell = columnCount > 1 ? new Cell(1, columnCount) : new Cell();
            fillerCell.setPadding(0);
            fillerCell.setBorder(Border.NO_BORDER);
            fillerCell.setHeight(fillerHeight);
            table.addCell(fillerCell);
        }
    }

    /**
     * 模型分页时每页的可用高度，与 PagingBuilder#fitpage 的计算保持一致（含预留的 5pt）。
     */
    private static int buildUsablePageHeight(Paper paper) {
        if (paper.getOrientation().equals(Orientation.landscape)) {
            return paper.getWidth() - paper.getBottomMargin() - paper.getTopMargin() - 5;
        }
        return paper.getHeight() - paper.getBottomMargin() - paper.getTopMargin() - 5;
    }

    /**
     * 每行的单元格数量与表格列数一致时才补填充行，避免行本身列数不齐时把表格撑乱。
     */
    private boolean isRowCellCountMatched(List<PdfPageRow> pageRows, int columnCount) {
        for (PdfPageRow pageRow : pageRows) {
            if (pageRow.cells.size() != columnCount) {
                return false;
            }
        }
        return true;
    }

    /**
     * 行高 = max(设计器声明的行高, 本行非跨行单元格的实测内容高度)。
     * 未开启「换行计算」的行保持模型计算的 realHeight（设计器行高、条件行高均不受影响）。
     */
    private int computeRowHeight(PdfPageRow pageRow) {
        int modelHeight = pageRow.row.getRealHeight();
        if (modelHeight < 1) {
            // 隐藏行/零高度行，保持模型结果
            return modelHeight;
        }
        boolean wrapCompute = false;
        float measured = 0;
        for (PdfPageCell pageCell : pageRow.cells) {
            if (pageCell.cellInfo.getPageRowSpan() > 1) {
                continue;
            }
            if (isWrapCompute(pageCell.cellInfo)) {
                wrapCompute = true;
            }
            if (pageCell.contentHeight > measured) {
                measured = pageCell.contentHeight;
            }
        }
        if (!wrapCompute) {
            return modelHeight;
        }
        return (int) Math.min(pageRow.row.getHeight(), Math.ceil(measured < modelHeight ? modelHeight : measured));
    }

    /**
     * 与 Cell.doDataWrapCompute 的触发条件保持一致。
     */
    private static boolean isWrapCompute(com.bstek.ureport.model.Cell cell) {
        CellStyle style = cell.getCellStyle();
        if (style == null) {
            return false;
        }
        Boolean wrapCompute = style.getWrapCompute();
        return wrapCompute != null && wrapCompute;
    }

    private static int sumRowHeights(int[] heights, int from, int to) {
        int total = 0;
        for (int i = from; i < to && i < heights.length; i++) {
            total += heights[i];
        }
        return total;
    }

    /**
     * 实测单元格内容的真实高度：把内容元素按单元格可用宽度排版一次，累加占用高度。
     * 单元格本身没有 iText 边框与内边距（边框由 CellBorderRenderer 直接画在单元格边界上），
     * 因此可用宽度就是单元格宽度本身。
     */
    private float measureCellContent(Cell cell, int cellWidth, IRenderer measureRootRenderer) {
        List<IElement> children = cell.getChildren();
        if (children == null || children.isEmpty()) {
            return 0;
        }
        float total = 0;
        for (IElement element : children) {
            IRenderer renderer = element.createRendererSubTree();
            renderer.setParent(measureRootRenderer);
            LayoutResult result = renderer.layout(new LayoutContext(
                    new LayoutArea(1, new Rectangle(cellWidth, CONTENT_MEASURE_MAX_HEIGHT))));
            if (result.getOccupiedArea() != null) {
                total += result.getOccupiedArea().getBBox().getHeight();
            }
        }
        return total;
    }

    /**
     * 一个待渲染的行：记录行本身与其中已构建好的单元格。
     */
    private static class PdfPageRow {
        private final Row row;
        private final List<PdfPageCell> cells = new ArrayList<PdfPageCell>();

        private PdfPageRow(Row row) {
            this.row = row;
        }
    }

    /**
     * 一个待渲染的单元格：模型单元格、iText 单元格与实测内容高度。
     */
    private static class PdfPageCell {
        private final com.bstek.ureport.model.Cell cellInfo;
        private final Cell pdfCell;
        private final float contentHeight;

        private PdfPageCell(com.bstek.ureport.model.Cell cellInfo, Cell pdfCell, float contentHeight) {
            this.cellInfo = cellInfo;
            this.pdfCell = pdfCell;
            this.contentHeight = contentHeight;
        }
    }

    /**
     * 安全关闭 Document。iText 7 中 Document.close() 会级联关闭 PdfDocument，
     * 重复关闭会抛出 IllegalStateException/PdfException，这里静默吞掉重复关闭异常。
     */
    private static void closeQuietly(Document document) {
        if (document == null) {
            return;
        }
        try {
            document.close();
        } catch (Exception ignored) {
            // 已经被关闭或 PdfWriter 关闭时静默忽略
        }
    }


    private int buildCellWidth(com.bstek.ureport.model.Cell cell, List<Column> columns) {
        int width = cell.getColumn().getWidth();
        int colSpan = cell.getColSpan();
        if (colSpan > 0) {
            int pos = columns.indexOf(cell.getColumn());
            int start = pos + 1, end = start + colSpan - 1;
            for (int i = start; i < end; i++) {
                width += columns.get(i).getWidth();
            }
        }
        return width;
    }

    private Cell buildPdfCell(com.bstek.ureport.model.Cell cellInfo, int cellWidth) throws Exception {
        CellStyle style = cellInfo.getCellStyle();
        CellStyle customStyle = cellInfo.getCustomCellStyle();
        CellStyle rowStyle = cellInfo.getRow().getCustomCellStyle();
        CellStyle colStyle = cellInfo.getColumn().getCustomCellStyle();

        int rowSpan = cellInfo.getPageRowSpan() > 0 ? cellInfo.getPageRowSpan() : 1;
        int colSpan = cellInfo.getColSpan() > 0 ? cellInfo.getColSpan() : 1;
        // iText 7 的 Cell 没有 setRowspan/setColspan，需要在构造时通过参数传入。
        // 必须用 BorderedCell：跨行合并单元格跨页时，iText 会经 Cell.getRenderer()/
        // Cell.clone() 重建续页渲染器，只有重写过这两个方法的子类才能保住自定义边框。
        Cell cell = newCell(cellInfo, rowSpan, colSpan, cellWidth, style, customStyle);
        cell.setPadding(0);
        cell.setBorder(Border.NO_BORDER);

        // 单元格高度不在这里设置，由 fillChildTable 实测内容高度后统一设置。
        // 必须让同一行的所有单元格（含按跨行数累加高度的合并单元格）拿到同一个高度：
        // iText 7 在切分跨页的行时，只会保留“自身高度等于行高”的单元格渲染器，
        // 被行高拉伸的较矮单元格在续页上会被整体丢弃（内容与边框一起消失），
        // 表现为跨页后同一行相邻单元格边框丢失。iText 5 对每个单元格都调用 setFixedHeight，
        // 这里保持一致，保证整行高度来源统一。
        if (colSpan > 1) {
            // iText 7 在 fixed layout 下默认会按 UnitValue 数组分配宽度，
            // 这里显式设置 Cell 宽度，确保跨列后总宽度与业务侧 cellWidth 一致。
            cell.setWidth(cellWidth);
        }

        Alignment align = style.getAlign();
        if (customStyle != null && customStyle.getAlign() != null) {
            align = customStyle.getAlign();
        }
        if (rowStyle != null && rowStyle.getAlign() != null) {
            align = rowStyle.getAlign();
        }
        if (colStyle != null && colStyle.getAlign() != null) {
            align = colStyle.getAlign();
        }
        if (align != null) {
            if (align.equals(Alignment.left)) {
                cell.setTextAlignment(TextAlignment.LEFT);
            } else if (align.equals(Alignment.center)) {
                cell.setTextAlignment(TextAlignment.CENTER);
            } else if (align.equals(Alignment.right)) {
                cell.setTextAlignment(TextAlignment.RIGHT);
            }
        }
        Alignment valign = style.getValign();
        if (customStyle != null && customStyle.getValign() != null) {
            valign = customStyle.getValign();
        }
        if (rowStyle != null && rowStyle.getValign() != null) {
            valign = rowStyle.getValign();
        }
        if (colStyle != null && colStyle.getValign() != null) {
            valign = colStyle.getValign();
        }
        if (valign != null) {
            if (valign.equals(Alignment.top)) {
                cell.setVerticalAlignment(VerticalAlignment.TOP);
            } else if (valign.equals(Alignment.middle)) {
                cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
            } else if (valign.equals(Alignment.bottom)) {
                cell.setVerticalAlignment(VerticalAlignment.BOTTOM);
            }
        }
        String bgcolor = style.getBgcolor();
        if (customStyle != null && StringUtils.isNotBlank(customStyle.getBgcolor())) {
            bgcolor = customStyle.getBgcolor();
        }
        if (rowStyle != null && StringUtils.isNotBlank(rowStyle.getBgcolor())) {
            bgcolor = rowStyle.getBgcolor();
        }
        if (colStyle != null && StringUtils.isNotBlank(colStyle.getBgcolor())) {
            bgcolor = colStyle.getBgcolor();
        }
        if (StringUtils.isNotEmpty(bgcolor)) {
            String[] colors = bgcolor.split(",");
            Color bg = new DeviceRgb(
                    Integer.valueOf(colors[0]),
                    Integer.valueOf(colors[1]),
                    Integer.valueOf(colors[2]));
            cell.setBackgroundColor(bg);
        }
        cell.setSplitCharacters(new ChineseSplitCharacter());
        return cell;
    }

    private int[] buildColumnSizeAndTotalWidth(List<Column> columns, List<Integer> list) {
        int count = 0, totalWidth = 0;
        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            int width = col.getWidth();
            if (width < 1) {
                continue;
            }
            count++;
            list.add(width);
            totalWidth += width;
        }
        return new int[]{count, totalWidth};
    }

    private Cell newCell(com.bstek.ureport.model.Cell cellInfo, int rowSpan, int colSpan, int cellWidth,
                         CellStyle style, CellStyle customStyle) throws Exception {
        // iText 7 的 Cell 没有 setRowspan/setColspan，只能在构造时指定。
        Cell cell = new BorderedCell(rowSpan, colSpan, style, customStyle);
        Object cellData = cellInfo.getFormatData();
        if (cellData instanceof Image) {
            Image img = (Image) cellData;
            cell.add(buildImageElement(img.getBase64Data(), 0, 0));
        } else if (cellData instanceof ChartData) {
            ChartData chartData = (ChartData) cellData;
            String base64Data = chartData.retriveBase64Data();
            if (base64Data != null) {
                Image img = new Image(base64Data, chartData.getWidth(), chartData.getHeight());
                cell.add(buildImageElement(img.getBase64Data(), 0, 0));
            } else {
                Paragraph paragraph = new CellPhrase().buildParagraph(cellInfo, "");
                cell.add(paragraph);
            }
        } else {
            if (cellData != null && isHtml(cellData.toString())) {
                String source = cellData.toString();

                CellPhrase cellPhrase = new CellPhrase();
                CellPhrase.ResolvedStyle rs = cellPhrase.resolveStyle(cellInfo);
                PdfFont font = FontBuilder.getFont(rs.fontName, rs.fontSize, rs.bold, rs.italic, rs.underline);
                String html = source;
                html = html.replaceAll("line-height:\\d+(\\w+|%);", "");
                int lineHeight = rs.fontSize;
                if (cellInfo.getCellStyle().getLineHeight() != 0L) {
                    lineHeight = (int) (rs.fontSize * cellInfo.getCellStyle().getLineHeight());
                }
                // 注意：iText 5 时代的
                //   "p:first-child{padding-top:10px} p:last-child{padding-bottom:10px}
                //    p:first-letter{margin-left:-1em}"
                // 这三条伪类规则必须去掉。XMLWorker 不支持伪类选择器，它们在 iText 5 下
                // 完全不生效（实测 Paragraph.paddingTop 始终为 0）；而 html2pdf 支持
                // :first-child/:last-child，会把 10px 上下内边距真正作用到单元格内的段落上
                // （px 按 0.75 折算，上下各 7.5pt），使每个 HTML 单元格比 iText 5 高出约 15pt，
                // 单元格高度、行位置与分页位置随之整体错位。
                // iText 5 时代 XMLWorker 对 p{} 的 white-space 支持有限，
                // 这里的 pre-wrap 实际是个 no-op，不会保留首尾/连续空格。
                // 迁移到 iText 7 后 html2pdf 完整支持 CSS white-space，
                // pre-wrap 会导致：HTML 源里的首尾空格（编辑器/模板常常带）被原样渲染出
                // 一个可见的“首行多字符”，连续多空格也不会被压回 1 个，与 iText 5 渲染不一致。
                // 改为 normal：合并连续空格、裁掉首尾，与 iText 5 行为对齐；
                // 同时 AidXMLWorkerHelper 里已把普通空格替换成 \u00a0 包裹形式，
                // 文本里真有想保留的多空格可以用 &nbsp; / 全角空格 / <pre> 等手段，
                // 不再依赖 pre-wrap 这个副作用。
                String css = "p{line-height:" + lineHeight + "pt;padding:0 0px;word-break:break-all;" +
                        "word-wrap:break-word;text-align:justify;white-space:normal;}"
                        // 上面的 word-break/word-wrap 只挂在 p{} 上，而 UReport 富文本编辑器产出的
                        // HTML 是 <span style="...">（外层是 html2pdf 造的隐式块，选择器 p 匹配不到），
                        // 于是「不可断的长串」（长英文单词/长数字/URL）会一行到底、冲出单元格右边界，
                        // 表现为「单元格内容显示不完，没换行」。
                        // 这里给所有元素补上 break-word：只在「整词放不下」时才断，
                        // 中文、英文句子、中英数混排的断行位置与原来逐字一致（实测无变化），
                        // 只是把原先溢出单元格的长串改成正常换行。
                        + "body,div,p,span,td,th{word-wrap:break-word;overflow-wrap:break-word;}";
                // 字体族/字号/粗体/斜体/下划线/颜色全部以单元格设置为准（与 iText 5 行为一致），
                // 否则 HTML 富文本会忽略表格上配置的字体样式
                List<IElement> elements = AidXMLWorkerHelper.parseToElementList(html, css, font, rs.fontName,
                        rs.fontSize, rs.bold, rs.italic, rs.underline, rs.forecolor);

                for (IElement e : elements) {
                    // iText 7 Cell 只能接收 IBlockElement 或 Image；HtmlConverter 输出混杂类型需分流
                    if (e instanceof IBlockElement) {
                        cell.add((IBlockElement) e);
                    } else if (e instanceof com.itextpdf.layout.element.Image) {
                        cell.add((com.itextpdf.layout.element.Image) e);
                    }
                }
            } else {
                Paragraph paragraph = new CellPhrase().buildParagraph(cellInfo, cellData);
                paragraph.setSplitCharacters(new ChineseSplitCharacter());
                cell.add(paragraph);
            }
        }
        CellStyle cellStyle = cellInfo.getCellStyle();
        if (cellStyle != null && cellStyle.getLineHeight() > 0) {
            // iText7 通过 Paragraph.multipliedLeading 控制行距；此处仅作为预留
        }
        cell.setSplitCharacters(new ChineseSplitCharacter());
        return cell;
    }

    public static boolean isHtml(String text) {
        return text.contains("<") && text.contains(">");
    }

    private com.itextpdf.layout.element.Image buildImageElement(String base64Data, int width, int height) throws Exception {
        InputStream input = ImageUtils.base64DataToInputStream(base64Data);
        try {
            byte[] bytes = IOUtils.toByteArray(input);
            ImageData imageData = ImageDataFactory.create(bytes);
            com.itextpdf.layout.element.Image image = new com.itextpdf.layout.element.Image(imageData);
            float imgWidth = image.getImageWidth();
            float imgHeight = image.getImageHeight();
            if (width == 0) {
                width = Float.valueOf(imgWidth).intValue();
            }
            if (height == 0) {
                height = Float.valueOf(imgHeight).intValue();
            }
            width = UnitUtils.pixelToPoint(width - 2);
            height = UnitUtils.pixelToPoint(height - 2);
            image.scaleToFit(width, height);
            return image;
        } finally {
            IOUtils.closeQuietly(input);
        }
    }
}