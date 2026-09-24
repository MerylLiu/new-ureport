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
package com.bstek.ureport.model;

import com.bstek.ureport.Range;
import com.bstek.ureport.Utils;
import com.bstek.ureport.builder.BindData;
import com.bstek.ureport.builder.Context;
import com.bstek.ureport.definition.*;
import com.bstek.ureport.definition.value.SimpleValue;
import com.bstek.ureport.definition.value.Value;
import com.bstek.ureport.exception.ReportComputeException;
import com.bstek.ureport.expression.model.Condition;
import com.bstek.ureport.expression.model.Expression;
import com.bstek.ureport.expression.model.data.BindDataListExpressionData;
import com.bstek.ureport.expression.model.data.ExpressionData;
import com.bstek.ureport.expression.model.data.ObjectExpressionData;
import com.bstek.ureport.expression.model.data.ObjectListExpressionData;
import com.bstek.ureport.utils.UnitUtils;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Jacky.gao
 * @since 2016年11月1日
 */
public class Cell implements ReportCell {
    private String name;
    private int rowSpan;
    private int colSpan;

    /**
     * 下面属性用于存放分页后的rowspan信息
     *
     */
    private int pageRowSpan = -1;

    private String renderBean;

    /**
     * 当前单元格计算后的实际值
     */
    private Object data;

    /**
     * 存储当前单元格对应值在进行格式化后的值
     */
    private Object formatData;

    private CellStyle cellStyle;
    private CellStyle customCellStyle;
    private Value value;
    private Row row;
    private Column column;
    private Expand expand;
    private boolean processed;
    private boolean blankCell;
    private boolean existPageFunction;
    private List<Object> bindData;
    private Range duplicateRange;
    private boolean forPaging;
    private String linkUrl;
    private String linkTargetWindow;
    private List<LinkParameter> linkParameters;

    private Map<String, String> linkParameterMap;

    private Expression linkUrlExpression;

    private List<ConditionPropertyItem> conditionPropertyItems;

    private boolean fillBlankRows;
    /**
     * 允许填充空白行时fillBlankRows=true，要求当前数据行数必须是multiple定义的行数的倍数，否则就补充空白行
     */
    private int multiple;

    /**
     * 当前单元格左父格
     */
    private Cell leftParentCell;
    /**
     * 当前单元格上父格
     */
    private Cell topParentCell;

    /**
     * 当前单元格所在行所有子格
     */
    private Map<String, List<Cell>> rowChildrenCellsMap = new HashMap<String, List<Cell>>();
    /**
     * 当前单元格所在列所有子格
     */
    private Map<String, List<Cell>> columnChildrenCellsMap = new HashMap<String, List<Cell>>();


    private List<String> increaseSpanCellNames;
    private Map<String, BlankCellInfo> newBlankCellsMap;
    private List<String> newCellNames;

    private final Set<Character> PUNCTUATION_SET;
    // 禁止出现在行首的标点（句末、右括号、右引号等），与 ChineseSplitCharacter 保持一致
    private static final String FORBIDDEN_LINE_START = "，。、；：？！）】》」』〕\u201D\u2019〉";

    public Cell() {
        String punct = "，。、；：？！‘’“”（）《》【】……——,.!?;:'\"()[]{}<>~`@#$%^&*_+-=\\/〔〕";
        PUNCTUATION_SET = new HashSet<>();
        for (char c : punct.toCharArray()) {
            PUNCTUATION_SET.add(c);
        }
    }

    public Cell newRowBlankCell(Context context, BlankCellInfo blankCellInfo, ReportCell mainCell) {
        Cell blankCell = newCell();
        blankCell.setBlankCell(true);
        blankCell.setValue(new SimpleValue(""));
        blankCell.setExpand(Expand.None);
        blankCell.setBindData(null);
        if (blankCellInfo != null) {
            int offset = blankCellInfo.getOffset();
            int mainRowNumber = mainCell.getRow().getRowNumber();
            if (offset == 0) {
                blankCell.setRow(mainCell.getRow());
            } else {
                int rowNumber = mainRowNumber + offset;
                Row row = context.getRow(rowNumber);
                blankCell.setRow(row);
            }
            blankCell.setRowSpan(blankCellInfo.getSpan());
        }
        return blankCell;
    }

    public Cell newColumnBlankCell(Context context, BlankCellInfo blankCellInfo, ReportCell mainCell) {
        Cell blankCell = newCell();
        blankCell.setBlankCell(true);
        blankCell.setValue(new SimpleValue(""));
        blankCell.setExpand(Expand.None);
        blankCell.setBindData(null);

        int offset = blankCellInfo.getOffset();
        int mainColNumber = mainCell.getColumn().getColumnNumber();
        if (offset == 0) {
            blankCell.setColumn(mainCell.getColumn());
        } else {
            int colNumber = mainColNumber + offset;
            Column col = context.getColumn(colNumber);
            blankCell.setColumn(col);
        }
        blankCell.setColSpan(blankCellInfo.getSpan());
        return blankCell;
    }

    public Cell newCell() {
        Cell cell = new Cell();
        cell.setColumn(column);
        cell.setRow(row);
        cell.setLeftParentCell(leftParentCell);
        cell.setTopParentCell(topParentCell);
        cell.setValue(value);
        cell.setRowSpan(rowSpan);
        cell.setColSpan(colSpan);
        cell.setExpand(expand);
        cell.setName(name);
        cell.setCellStyle(cellStyle);
        cell.setNewBlankCellsMap(newBlankCellsMap);
        cell.setNewCellNames(newCellNames);
        cell.setIncreaseSpanCellNames(increaseSpanCellNames);
        cell.setDuplicateRange(duplicateRange);
        cell.setLinkParameters(linkParameters);
        cell.setLinkTargetWindow(linkTargetWindow);
        cell.setLinkUrl(linkUrl);
        cell.setPageRowSpan(pageRowSpan);
        cell.setConditionPropertyItems(conditionPropertyItems);
        cell.setFillBlankRows(fillBlankRows);
        cell.setMultiple(multiple);
        cell.setLinkUrlExpression(linkUrlExpression);
        return cell;
    }

    public void addRowChild(Cell child) {
        String name = child.getName();
        List<Cell> cells = rowChildrenCellsMap.get(name);
        if (cells == null) {
            cells = new ArrayList<Cell>();
            rowChildrenCellsMap.put(name, cells);
        }
        if (!cells.contains(child)) {
            cells.add(child);
        }
        if (leftParentCell != null) {
            leftParentCell.addRowChild(child);
        }
    }


    public void addColumnChild(Cell child) {
        String name = child.getName();
        List<Cell> cells = columnChildrenCellsMap.get(name);
        if (cells == null) {
            cells = new ArrayList<Cell>();
            columnChildrenCellsMap.put(name, cells);
        }
        if (!cells.contains(child)) {
            cells.add(child);
        }
        if (topParentCell != null) {
            topParentCell.addColumnChild(child);
        }
    }

    @Override
    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Object getFormatData() {
        if (formatData == null) {
            return data;
        }
        return formatData;
    }

    public void setFormatData(Object formatData) {
        this.formatData = formatData;
    }

    public void doCompute(Context context) {
        doComputeConditionProperty(context);
        doFormat();
        doDataWrapCompute(context);
    }

    public void doFormat() {
        String format = cellStyle.getFormat();
        String customFormat = null;
        if (customCellStyle != null) {
            customFormat = customCellStyle.getFormat();
        }
        if (StringUtils.isNotBlank(customFormat)) {
            format = customFormat;
        }
        if (StringUtils.isBlank(format) || data == null || StringUtils.isBlank(data.toString())) {
            return;
        }
        if (data instanceof Date) {
            Date d = (Date) data;
            SimpleDateFormat sd = new SimpleDateFormat(format);
            formatData = sd.format(d);
        } else {
            BigDecimal bd = null;
            try {
                bd = Utils.toBigDecimal(data);
            } catch (Exception ex) {
            }
            if (bd != null) {
                DecimalFormat df = new DecimalFormat(format);
                formatData = df.format(bd.doubleValue());
            }
        }
    }

    private void doComputeConditionProperty(Context context) {
        if (conditionPropertyItems == null || conditionPropertyItems.size() == 0) {
            return;
        }
        for (ConditionPropertyItem item : conditionPropertyItems) {
            Condition condition = item.getCondition();
            if (condition == null) {
                continue;
            }
            Object obj = null;
            List<Object> bindDataList = this.bindData;
            if (bindDataList != null && bindDataList.size() > 0) {
                obj = bindDataList.get(0);
            }
            if (!condition.filter(this, this, obj, context)) {
                continue;
            }
            ConditionPaging paging = item.getPaging();
            if (paging != null) {
                PagingPosition position = paging.getPosition();
                if (position != null) {
                    if (position.equals(PagingPosition.after)) {
                        int line = paging.getLine();
                        if (line == 0) {
                            row.setPageBreak(true);
                        } else {
                            int rowNumber = row.getRowNumber() + line;
                            Row targetRow = context.getRow(rowNumber);
                            targetRow.setPageBreak(true);
                        }

                    } else {
                        int rowNumber = row.getRowNumber() - 1;
                        Row targetRow = context.getRow(rowNumber);
                        targetRow.setPageBreak(true);
                    }
                }
            }

            int rowHeight = item.getRowHeight();
            if (rowHeight > -1) {
                row.setRealHeight(rowHeight);
                if (rowHeight == 0 && !row.isHide()) {
                    context.doHideProcessRow(row);
                }
            }
            int colWidth = item.getColWidth();
            if (colWidth > -1) {
                column.setWidth(colWidth);
                if (colWidth == 0 && !column.isHide()) {
                    context.doHideProcessColumn(column);
                }
            }
            if (StringUtils.isNotBlank(item.getNewValue())) {
                this.data = item.getNewValue();
                this.formatData = item.getNewValue();
            }
            if (StringUtils.isNotBlank(item.getLinkUrl())) {
                linkUrl = item.getLinkUrl();
                if (StringUtils.isNotBlank(item.getLinkTargetWindow())) {
                    linkTargetWindow = item.getLinkTargetWindow();
                }
                if (item.getLinkParameters() != null && item.getLinkParameters().size() > 0) {
                    linkParameters = item.getLinkParameters();
                }
            }
            ConditionCellStyle style = item.getCellStyle();
            if (style != null) {
                Boolean bold = style.getBold();
                if (bold != null) {
                    Scope scope = style.getBoldScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setBold(bold);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setBold(bold);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setBold(bold);
                    }
                }
                Boolean italic = style.getItalic();
                if (italic != null) {
                    Scope scope = style.getItalicScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setItalic(italic);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setItalic(italic);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setItalic(italic);
                    }
                }
                Boolean underline = style.getUnderline();
                if (underline != null) {
                    Scope scope = style.getUnderlineScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setUnderline(underline);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setUnderline(underline);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setUnderline(underline);
                    }

                }
                String forecolor = style.getForecolor();
                if (StringUtils.isNotBlank(forecolor)) {
                    Scope scope = style.getForecolorScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setForecolor(forecolor);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setForecolor(forecolor);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setForecolor(forecolor);
                    }
                }
                String bgcolor = style.getBgcolor();
                if (StringUtils.isNotBlank(bgcolor)) {
                    Scope scope = style.getBgcolorScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setBgcolor(bgcolor);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setBgcolor(bgcolor);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setBgcolor(bgcolor);
                    }
                }
                int fontSize = style.getFontSize();
                if (fontSize > 0) {
                    Scope scope = style.getFontSizeScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setFontSize(fontSize);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setFontSize(fontSize);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setFontSize(fontSize);
                    }
                }
                String fontFamily = style.getFontFamily();
                if (StringUtils.isNotBlank(fontFamily)) {
                    Scope scope = style.getFontFamilyScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setFontFamily(fontFamily);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setFontFamily(fontFamily);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setFontFamily(fontFamily);
                    }
                }
                String format = style.getFormat();
                if (StringUtils.isNotBlank(format)) {
                    if (this.customCellStyle == null) {
                        this.customCellStyle = new CellStyle();
                    }
                    this.customCellStyle.setFormat(format);
                }
                Alignment align = style.getAlign();
                if (align != null) {
                    Scope scope = style.getAlignScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setAlign(align);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setAlign(align);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setAlign(align);
                    }
                }
                Alignment valign = style.getValign();
                if (valign != null) {
                    Scope scope = style.getValignScope();
                    if (scope.equals(Scope.cell)) {
                        if (this.customCellStyle == null) {
                            this.customCellStyle = new CellStyle();
                        }
                        this.customCellStyle.setValign(valign);
                    } else if (scope.equals(Scope.row)) {
                        if (row.getCustomCellStyle() == null) {
                            row.setCustomCellStyle(new CellStyle());
                        }
                        row.getCustomCellStyle().setValign(valign);
                    } else if (scope.equals(Scope.column)) {
                        if (column.getCustomCellStyle() == null) {
                            column.setCustomCellStyle(new CellStyle());
                        }
                        column.getCustomCellStyle().setValign(valign);
                    }
                }
                Border leftBorder = style.getLeftBorder();
                if (leftBorder != null) {
                    this.customCellStyle.setLeftBorder(leftBorder);
                }
                Border rightBorder = style.getRightBorder();
                if (rightBorder != null) {
                    this.customCellStyle.setRightBorder(rightBorder);
                }
                Border topBorder = style.getTopBorder();
                if (topBorder != null) {
                    this.customCellStyle.setTopBorder(topBorder);
                }
                Border bottomBorder = style.getBottomBorder();
                if (bottomBorder != null) {
                    this.customCellStyle.setBottomBorder(bottomBorder);
                }
            }
        }
    }

    public void doDataWrapCompute(Context context) {
        Boolean wrapCompute = cellStyle.getWrapCompute();
        if (wrapCompute == null || !wrapCompute) {
            return;
        }
        Object targetData = getFormatData();
        if (targetData == null || !(targetData instanceof String)) {
            return;
        }
        String dataText = targetData.toString();
        if (StringUtils.isBlank(dataText) || dataText.length() < 2) {
            return;
        }
        // 富文本单元格必须先走 HTML 分支：formatData 里存的是带标签与样式属性的 HTML 源码，
        // 若像普通文本那样按整串字符数估算行数，一个只有几个字的单元格也会被算成十几行，
        // row.realHeight 被撑得远大于实际渲染高度；PdfProducer 侧又会执行
        // cell.setHeight(realHeight) 把整行拉高，表现为「数据撑开行后与下一行间距过大」。
        if (isHtmlContent(dataText)) {
            doHtmlWrapCompute(context, dataText);
            return;
        }
        int totalColumnWidth = column.getWidth();
        if (colSpan > 0) {
            int colNumber = column.getColumnNumber();
            for (int i = 1; i < colSpan; i++) {
                Column col = context.getColumn(colNumber + i);
                totalColumnWidth += col.getWidth();
            }
        }
        Font font = cellStyle.getFont();
        JLabel jlabel = new JLabel();
        FontMetrics fontMetrics = jlabel.getFontMetrics(font);
        int textWidth = fontMetrics.stringWidth(dataText);

        double fontSize = font.getSize();//cellStyle.getFontSize();
        float lineHeight = 1.2f;
        if (cellStyle.getLineHeight() > 0) {
            lineHeight = cellStyle.getLineHeight();
        }
        fontSize = fontSize * lineHeight;
        int singleLineHeight = UnitUtils.pointToPixel(fontSize) - 2;//fontMetrics.getHeight();
        if (textWidth <= totalColumnWidth) {
            return;
        }
        int totalLineHeight = 0;
        StringBuilder multipleLine = new StringBuilder();
        StringBuilder sb = new StringBuilder();
        int length = dataText.length();
        for (int i = 0; i < length; i++) {
            char text = dataText.charAt(i);
            if (text == '\r' || text == '\n') {
                if (text == '\r') {
                    int nextIndex = i + 1;
                    if (nextIndex < length) {
                        char nextText = dataText.charAt(nextIndex);
                        if (nextText == '\n') {
                            i = nextIndex;
                        }
                    }
                }
                continue;
            }
            sb.append(text);

            int width = fontMetrics.stringWidth(sb.toString()) + 4;
            if (width > totalColumnWidth) {
                sb.deleteCharAt(sb.length() - 1); // 移除刚追加的 text
                boolean forbidden = FORBIDDEN_LINE_START.indexOf(text) != -1;

                if (forbidden && sb.length() > 0) {
                    // 禁排标点不能做新行首：连同前一字符一起移到下一行，
                    // 既避免标点出现在行首，又不让当前行超出列宽
                    char prevChar = sb.charAt(sb.length() - 1);
                    sb.deleteCharAt(sb.length() - 1);
                    totalLineHeight += singleLineHeight;
                    if (multipleLine.length() > 0) {
                        multipleLine.append('\n');
                    }
                    multipleLine.append(sb);
                    sb.delete(0, sb.length());
                    sb.append(prevChar);
                    sb.append(text);
                } else if (forbidden) {
                    // sb 为空（无法再取前一字符），将标点悬挂在当前行尾
                    sb.append(text);
                    totalLineHeight += singleLineHeight;
                    if (multipleLine.length() > 0) {
                        multipleLine.append('\n');
                    }
                    multipleLine.append(sb);
                    sb.delete(0, sb.length());
                } else {
                    // 普通换行：text 作为新行首字符
                    totalLineHeight += singleLineHeight;
                    if (multipleLine.length() > 0) {
                        multipleLine.append('\n');
                    }
                    multipleLine.append(sb);
                    sb.delete(0, sb.length());
                    sb.append(text);
                }
            }
        }

        if (sb.length() > 0 && FORBIDDEN_LINE_START.indexOf(sb.charAt(0)) != -1) {
            try {
                int idx = 1;
                while (idx < multipleLine.length()) {
                    String lastLineEnd = multipleLine.substring(multipleLine.length() - idx);
                    if (FORBIDDEN_LINE_START.indexOf(lastLineEnd.charAt(0)) != -1) {
                        idx++;
                    } else {
                        break;
                    }
                }

                sb.insert(0, multipleLine.substring(multipleLine.length() - idx));
                multipleLine.delete(multipleLine.length() - idx, multipleLine.length());
            } catch (Exception e) {
            }
        }
        if (sb.length() > 0) {
            totalLineHeight += singleLineHeight;
            if (multipleLine.length() > 0) {
                multipleLine.append('\n');
            }
            multipleLine.append(sb);
        }

        this.formatData = handleDigit(multipleLine.toString());
        int totalRowHeight = row.getHeight();
        if (rowSpan > 0) {
            int rowNumber = row.getRowNumber();
            for (int i = 1; i < rowSpan; i++) {
                Row targetRow = context.getRow(rowNumber + i);
                totalRowHeight += targetRow.getHeight();
            }
        }
        int dif = totalLineHeight - totalRowHeight;
        if (dif > 0) {
            int rowHeight = row.getHeight();
            int newRowHeight = rowHeight + dif;
            if (row.getRealHeight() < newRowHeight) {
                row.setRealHeight(newRowHeight);
            }
        }
    }

    /**
     * 是否富文本内容。与 PdfProducer.isHtml 的判定保持一致，
     * 避免「一边按 HTML 渲染、一边按纯文本估算行高」的错配。
     */
    public static boolean isHtmlContent(String text) {
        return text != null && text.indexOf('<') > -1 && text.indexOf('>') > -1;
    }

    /**
     * 富文本（HTML）单元格的高度自适应估算。
     * <p>
     * 与普通文本的关键差别：
     * <ul>
     * <li>行数只能按<b>可见文字</b>估算：标签、属性、样式声明都不占排版宽度，必须剔除；
     * 否则 markup 越长，估算出的行数越多，realHeight 越离谱。</li>
     * <li>不能回写 formatData：这里保存的是 HTML 源码，插入换行/改写会破坏富文本内容
     * （原实现按字符切割并插入 '\n'，对 HTML 来说属于误伤）。</li>
     * </ul>
     */
    private void doHtmlWrapCompute(Context context, String html) {
        int totalColumnWidth = column.getWidth();
        if (colSpan > 0) {
            int colNumber = column.getColumnNumber();
            for (int i = 1; i < colSpan; i++) {
                Column col = context.getColumn(colNumber + i);
                totalColumnWidth += col.getWidth();
            }
        }
        String text = stripHtmlTags(html);
        if (StringUtils.isBlank(text)) {
            return;
        }
        Font font = cellStyle.getFont();
        JLabel jlabel = new JLabel();
        FontMetrics fontMetrics = jlabel.getFontMetrics(font);
        int textWidth = fontMetrics.stringWidth(text);
        if (textWidth <= totalColumnWidth) {
            return;
        }
        double fontSize = font.getSize();
        float lineHeight = 1.2f;
        if (cellStyle.getLineHeight() > 0) {
            lineHeight = cellStyle.getLineHeight();
        }
        fontSize = fontSize * lineHeight;
        int singleLineHeight = UnitUtils.pointToPixel(fontSize) - 2;
        int lines = (int) Math.ceil((double) textWidth / totalColumnWidth);
        int totalLineHeight = lines * singleLineHeight;
        int totalRowHeight = row.getHeight();
        if (rowSpan > 0) {
            int rowNumber = row.getRowNumber();
            for (int i = 1; i < rowSpan; i++) {
                Row targetRow = context.getRow(rowNumber + i);
                totalRowHeight += targetRow.getHeight();
            }
        }
        int dif = totalLineHeight - totalRowHeight;
        if (dif > 0) {
            int newRowHeight = row.getHeight() + dif;
            if (row.getRealHeight() < newRowHeight) {
                row.setRealHeight(newRowHeight);
            }
        }
    }

    /**
     * 去掉 HTML 标签，并按常见实体还原为一个可见字符，仅用于宽度/行数估算。
     */
    private static String stripHtmlTags(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?s)<[^>]*>", "");
        // 标签去掉后残留的实体按单个字符参与估算，避免高估宽度
        text = text.replace("&nbsp;", " ").replace("&NBSP;", " ")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'");
        return text;
    }

    @Override
    public CellStyle getCellStyle() {
        return cellStyle;
    }

    public void setCellStyle(CellStyle cellStyle) {
        this.cellStyle = cellStyle;
    }

    public CellStyle getCustomCellStyle() {
        return customCellStyle;
    }

    public void setCustomCellStyle(CellStyle customCellStyle) {
        this.customCellStyle = customCellStyle;
    }

    public boolean isBlankCell() {
        return blankCell;
    }

    public void setBlankCell(boolean blankCell) {
        this.blankCell = blankCell;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getRowSpan() {
        return rowSpan;
    }

    public void setRowSpan(int rowSpan) {
        this.rowSpan = rowSpan;
    }

    @Override
    public int getColSpan() {
        return colSpan;
    }

    public void setColSpan(int colSpan) {
        this.colSpan = colSpan;
    }

    public int getPageRowSpan() {
        if (pageRowSpan == -1) {
            return rowSpan;
        }
        return pageRowSpan;
    }

    public void setPageRowSpan(int pageRowSpan) {
        this.pageRowSpan = pageRowSpan;
    }

    @Override
    public Row getRow() {
        return row;
    }

    public void setRow(Row row) {
        this.row = row;
    }

    @Override
    public Column getColumn() {
        return column;
    }

    public void setColumn(Column column) {
        this.column = column;
    }

    @Override
    public Value getValue() {
        return value;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    public String getRenderBean() {
        return renderBean;
    }

    public void setRenderBean(String renderBean) {
        this.renderBean = renderBean;
    }

    public void setForPaging(boolean forPaging) {
        this.forPaging = forPaging;
    }

    public boolean isForPaging() {
        return forPaging;
    }

    public Range getDuplicateRange() {
        return duplicateRange;
    }

    public void setDuplicateRange(Range duplicateRange) {
        this.duplicateRange = duplicateRange;
    }

    public Map<String, List<Cell>> getRowChildrenCellsMap() {
        return rowChildrenCellsMap;
    }

    public Map<String, List<Cell>> getColumnChildrenCellsMap() {
        return columnChildrenCellsMap;
    }

    public List<ConditionPropertyItem> getConditionPropertyItems() {
        return conditionPropertyItems;
    }

    public void setConditionPropertyItems(List<ConditionPropertyItem> conditionPropertyItems) {
        this.conditionPropertyItems = conditionPropertyItems;
    }

    @Override
    public Expand getExpand() {
        return expand;
    }

    public void setExpand(Expand expand) {
        this.expand = expand;
    }

    public Cell getLeftParentCell() {
        return leftParentCell;
    }

    public void setLeftParentCell(Cell leftParentCell) {
        this.leftParentCell = leftParentCell;
    }

    public Cell getTopParentCell() {
        return topParentCell;
    }

    public void setTopParentCell(Cell topParentCell) {
        this.topParentCell = topParentCell;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public boolean isExistPageFunction() {
        return existPageFunction;
    }

    public void setExistPageFunction(boolean existPageFunction) {
        this.existPageFunction = existPageFunction;
    }

    @Override
    public List<Object> getBindData() {
        return bindData;
    }

    public void setBindData(List<Object> bindData) {
        this.bindData = bindData;
    }

    public List<String> getIncreaseSpanCellNames() {
        return increaseSpanCellNames;
    }

    public void setIncreaseSpanCellNames(List<String> increaseSpanCellNames) {
        this.increaseSpanCellNames = increaseSpanCellNames;
    }

    public Map<String, BlankCellInfo> getNewBlankCellsMap() {
        return newBlankCellsMap;
    }

    public void setNewBlankCellsMap(Map<String, BlankCellInfo> newBlankCellsMap) {
        this.newBlankCellsMap = newBlankCellsMap;
    }

    public List<String> getNewCellNames() {
        return newCellNames;
    }

    public void setNewCellNames(List<String> newCellNames) {
        this.newCellNames = newCellNames;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getLinkTargetWindow() {
        return linkTargetWindow;
    }

    public void setLinkTargetWindow(String linkTargetWindow) {
        this.linkTargetWindow = linkTargetWindow;
    }

    public List<LinkParameter> getLinkParameters() {
        return linkParameters;
    }

    public void setLinkParameters(List<LinkParameter> linkParameters) {
        this.linkParameters = linkParameters;
    }

    public String buildLinkParameters(Context context) {
        StringBuilder sb = new StringBuilder();
        if (linkParameters != null) {
            for (int i = 0; i < linkParameters.size(); i++) {
                LinkParameter param = linkParameters.get(i);
                String name = param.getName();
                if (linkParameterMap == null) {
                    linkParameterMap = new HashMap<String, String>();
                }
                String value = linkParameterMap.get(name);
                if (value == null) {
                    Expression expr = param.getValueExpression();
                    value = buildExpression(context, name, expr);
                }
                try {
                    value = URLEncoder.encode(value, "utf-8");
                    value = URLEncoder.encode(value, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    throw new ReportComputeException(e);
                }
                if (i > 0) {
                    sb.append("&");
                }
                sb.append(name + "=" + value);
            }
        }
        return sb.toString();
    }

    public boolean isFillBlankRows() {
        return fillBlankRows;
    }

    public void setFillBlankRows(boolean fillBlankRows) {
        this.fillBlankRows = fillBlankRows;
    }

    public int getMultiple() {
        return multiple;
    }

    public void setMultiple(int multiple) {
        this.multiple = multiple;
    }

    public Expression getLinkUrlExpression() {
        return linkUrlExpression;
    }

    public void setLinkUrlExpression(Expression linkUrlExpression) {
        this.linkUrlExpression = linkUrlExpression;
    }

    private String buildExpression(Context context, String name, Expression expr) {
        ExpressionData<?> exprData = expr.execute(this, this, context);
        if (exprData instanceof ObjectListExpressionData) {
            ObjectListExpressionData listData = (ObjectListExpressionData) exprData;
            List<?> list = listData.getData();
            StringBuilder dataSB = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                Object obj = list.get(i);
                if (obj == null) {
                    obj = "null";
                }
                if (i > 0) {
                    dataSB.append(",");
                }
                dataSB.append(obj);
            }
            linkParameterMap.put(name, dataSB.toString());
            return dataSB.toString();
        } else if (exprData instanceof ObjectExpressionData) {
            ObjectExpressionData data = (ObjectExpressionData) exprData;
            Object obj = data.getData();
            if (obj == null) {
                obj = "null";
            } else if (obj instanceof String) {
                obj = (String) obj;
            }
            linkParameterMap.put(name, obj.toString());
            return obj.toString();
        } else if (exprData instanceof BindDataListExpressionData) {
            BindDataListExpressionData bindDataListData = (BindDataListExpressionData) exprData;
            List<BindData> list = bindDataListData.getData();
            if (list.size() == 1) {
                Object data = list.get(0).getValue();
                if (data != null) {
                    return data.toString();
                } else {
                    return "";
                }
            } else if (list.size() > 1) {
                StringBuilder sb = new StringBuilder();
                for (BindData bindData : list) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    Object data = bindData.getValue();
                    if (data != null) {
                        sb.append(data.toString());
                    }
                }
                return sb.toString();
            }
        }
        return "";
    }

    private String handleDigit(String text) {
        Pattern startpattern = Pattern.compile("^(,?\\d)+(\\.?\\d+)+");
        Pattern endPattern = Pattern.compile("-?(\\d+(?:,?\\d*)*(?:\\.\\d{1,2})?)$");

        String[] split = text.split("\n");
        for (int i = 0; i < split.length; i++) {
            if (i > 0) {
                String lastLine = split[i - 1];
                String currLine = split[i];

                if (currLine.length() == 1) {
                    split[i - 1] = lastLine.substring(0, lastLine.length() - 1);
                    split[i] = lastLine.substring(lastLine.length() - 1) + currLine;
                }

                Matcher startMatcher = startpattern.matcher(currLine);
                Matcher endMatcher = endPattern.matcher(lastLine);
                int endLength = getMatchedLength(endMatcher);
                int startLength = getMatchedLength(startMatcher);

                if (endLength > 0 && startLength > 0) {
                    split[i - 1] = lastLine.substring(0, lastLine.length() - endLength);
                    split[i] = lastLine.substring(lastLine.length() - endLength) + currLine;
                }

                // 行首禁排兜底：当前行以禁排标点开头时，从上一行末尾向前
                // 找到第一个非禁排字符，将其及之后的全部字符前移到当前行
                if (currLine.length() > 0
                        && lastLine.length() > 1
                        && FORBIDDEN_LINE_START.indexOf(currLine.charAt(0)) != -1) {
                    int moveCount = 0;
                    for (int j = lastLine.length() - 1; j >= 0; j--) {
                        moveCount++;
                        if (FORBIDDEN_LINE_START.indexOf(lastLine.charAt(j)) == -1) {
                            break;
                        }
                    }
                    if (moveCount < lastLine.length()) {
                        split[i - 1] = lastLine.substring(0, lastLine.length() - moveCount);
                        split[i] = lastLine.substring(lastLine.length() - moveCount) + currLine;
                    }
                }
            }
        }

        return String.join("\n", split);
    }

    public int getMatchedLength(Matcher matcher) {
        int length = 0;
        while (matcher.find()) {
            length += matcher.group().length();
        }
        return length;
    }

    public static void main(String[] args) {
        String text = "维修项目2,00";
        Pattern p = Pattern.compile("-?(\\d+(?:,?\\d*)*(?:\\.\\d{1,2})?)$");
        Matcher m = p.matcher(text);
        if (m.find()) {
            System.out.println("最后一个数字：" + m.group());
            System.out.println("起始下标：" + m.start());
        }
    }

}
