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

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.bstek.ureport.definition.Border;
import com.bstek.ureport.definition.BorderStyle;
import com.bstek.ureport.definition.CellStyle;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.renderer.CellRenderer;
import com.itextpdf.layout.renderer.DrawContext;
import com.itextpdf.layout.renderer.IRenderer;

/**
 * iText 7 中不再使用 PdfPCellEvent，改用 CellRenderer.drawBorder 实现自定义边框。
 * 通过调用 Cell.setNextRenderer() 接入。
 */
public class CellBorderEvent {

    private CellStyle style;
    private CellStyle customStyle;

    public CellBorderEvent(CellStyle style, CellStyle customStyle) {
        this.style = style;
        this.customStyle = customStyle;
    }

    /**
     * 将自定义边框渲染器绑定到目标 Cell。
     */
    public void applyTo(Cell cell) {
        cell.setNextRenderer(new BorderCellRenderer(cell, style, customStyle));
    }

    public static class BorderCellRenderer extends CellRenderer {
        private static final long serialVersionUID = 1L;
        private final CellStyle style;
        private final CellStyle customStyle;

        public BorderCellRenderer(Cell modelElement, CellStyle style, CellStyle customStyle) {
            super(modelElement);
            this.style = style;
            this.customStyle = customStyle;
        }

        /**
         * 分页/溢出时必须返回同一类型的渲染器，否则边框会丢失。
         * iText 7 的 CellRenderer.createSplitRenderer()/createOverflowRenderer() 内部都是
         * 通过 getNextRenderer() 得到新渲染器，而 CellRenderer.getNextRenderer() 默认
         * new 一个普通 CellRenderer，导致表格跨页后自定义边框渲染器被替换、边框不再绘制。
         */
        @Override
        public IRenderer getNextRenderer() {
            return new BorderCellRenderer((Cell) getModelElement(), style, customStyle);
        }

        @Override
        public void draw(DrawContext drawContext) {
            // iText 7 的默认绘制顺序是 背景 → 边框 → 子元素，
            // 富文本 HTML 内容（带背景色的 div/span）会覆盖先画的边框；
            // iText 5 中边框总是最后绘制。这里改为在全部内容绘制完成后再画自定义边框。
            super.draw(drawContext);
            drawCustomBorders(drawContext);
        }

        private void drawCustomBorders(DrawContext drawContext) {
            RectangleSizer area = new RectangleSizer(getOccupiedArea().getBBox().getX(),
                    getOccupiedArea().getBBox().getY(),
                    getOccupiedArea().getBBox().getX() + getOccupiedArea().getBBox().getWidth(),
                    getOccupiedArea().getBBox().getY() + getOccupiedArea().getBBox().getHeight());
            PdfCanvas canvas = drawContext.getCanvas();
            drawLeft(canvas, area);
            drawTop(canvas, area);
            drawRight(canvas, area);
            drawBottom(canvas, area);
        }

        private void drawLeft(PdfCanvas canvas, RectangleSizer p) {
            Border leftBorder = style.getLeftBorder();
            if (customStyle != null && customStyle.getLeftBorder() != null) {
                leftBorder = customStyle.getLeftBorder();
            }
            if (leftBorder != null) {
                applyBorderStyle(canvas, leftBorder);
                canvas.moveTo(p.left, p.top);
                canvas.lineTo(p.left, p.bottom);
                canvas.stroke();
                if (leftBorder.getStyle().equals(BorderStyle.doublesolid)) {
                    canvas.moveTo(p.left + 2, p.top - 2);
                    canvas.lineTo(p.left + 2, p.bottom + 2);
                    canvas.stroke();
                }
            }
        }

        private void drawTop(PdfCanvas canvas, RectangleSizer p) {
            Border topBorder = style.getTopBorder();
            if (customStyle != null && customStyle.getTopBorder() != null) {
                topBorder = customStyle.getTopBorder();
            }
            if (topBorder != null) {
                applyBorderStyle(canvas, topBorder);
                canvas.moveTo(p.left, p.top);
                canvas.lineTo(p.right, p.top);
                canvas.stroke();
                if (topBorder.getStyle().equals(BorderStyle.doublesolid)) {
                    canvas.moveTo(p.left + 2, p.top - 2);
                    canvas.lineTo(p.right - 2, p.top - 2);
                    canvas.stroke();
                }
            }
        }

        private void drawRight(PdfCanvas canvas, RectangleSizer p) {
            Border rightBorder = style.getRightBorder();
            if (customStyle != null && customStyle.getRightBorder() != null) {
                rightBorder = customStyle.getRightBorder();
            }
            if (rightBorder != null) {
                applyBorderStyle(canvas, rightBorder);
                canvas.moveTo(p.right, p.top);
                canvas.lineTo(p.right, p.bottom);
                canvas.stroke();
                if (rightBorder.getStyle().equals(BorderStyle.doublesolid)) {
                    canvas.moveTo(p.right - 2, p.top - 2);
                    canvas.lineTo(p.right - 2, p.bottom + 2);
                    canvas.stroke();
                }
            }
        }

        private void drawBottom(PdfCanvas canvas, RectangleSizer p) {
            Border bottomBorder = style.getBottomBorder();
            if (customStyle != null && customStyle.getBottomBorder() != null) {
                bottomBorder = customStyle.getBottomBorder();
            }
            if (bottomBorder != null) {
                applyBorderStyle(canvas, bottomBorder);
                canvas.moveTo(p.left, p.bottom);
                canvas.lineTo(p.right, p.bottom);
                canvas.stroke();
                if (bottomBorder.getStyle().equals(BorderStyle.doublesolid)) {
                    canvas.moveTo(p.left + 2, p.bottom + 2);
                    canvas.lineTo(p.right - 2, p.bottom + 2);
                    canvas.stroke();
                }
            }
        }

        private void applyBorderStyle(PdfCanvas canvas, Border border) {
            BigDecimal w = new BigDecimal(border.getWidth() > 0 ? border.getWidth() : 1);
            float lineWidth = w.divide(new BigDecimal(2), 10, RoundingMode.HALF_UP).floatValue();
            canvas.setLineWidth(lineWidth);
            if (border.getStyle() != null && border.getStyle().equals(BorderStyle.dashed)) {
                // iText 7 PdfCanvas.setLineDash 签名: (float[] units, float phase)
                canvas.setLineDash(new float[]{3f, 1f}, 2f);
            } else {
                // 重置虚线状态，避免上一次虚线设置残留
                canvas.setLineDash(0f);
            }
            // 颜色未设置时默认黑色，避免 NPE 导致渲染中断、边框缺失
            String colorStr = border.getColor();
            if (colorStr == null || colorStr.trim().isEmpty()) {
                colorStr = "0,0,0";
            }
            String borderColor[] = colorStr.split(",");
            Color color = new DeviceRgb(
                    Integer.valueOf(borderColor[0].trim()),
                    Integer.valueOf(borderColor[1].trim()),
                    Integer.valueOf(borderColor[2].trim()));
            canvas.setStrokeColor(color);
        }
    }

    /**
     * 边框绘制区域（PDF 用户坐标系：原点在左下，y 向上）。
     */
    private static class RectangleSizer {
        final float left;
        final float right;
        final float top;
        final float bottom;

        RectangleSizer(float left, float bottom, float right, float top) {
            this.left = left;
            this.right = right;
            this.bottom = bottom;
            this.top = top;
        }
    }
}