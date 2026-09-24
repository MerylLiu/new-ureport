package com.bstek.ureport.export.pdf;

import com.bstek.ureport.definition.CellStyle;
import com.itextpdf.layout.Style;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.IElement;
import com.itextpdf.layout.renderer.IRenderer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 挂载 ureport 自定义边框的 iText 7 Cell。
 * <p>
 * 之前的做法是 {@code cell.setNextRenderer(new BorderCellRenderer(...))}，但 setNextRenderer
 * 只在渲染树首次创建（createRendererSubTree/makeNewRenderer）时生效一次。iText 7.2.5 的
 * {@code TableRenderer} 在切分<b>跨行合并</b>单元格时会绕过 nextRenderer，直接在模型元素上调用
 * {@code Cell.getRenderer()} 或 {@code Cell.clone(true).getRenderer()} 重建续页部分的渲染器
 * （对应 enlargeCell / enlargeCellWithBigRowspan 路径），两者都会返回普通 CellRenderer，
 * 自定义边框渲染器被替换，表现为「单元格跨页后边框消失」。
 * <p>
 * 因此改为继承 Cell 并重写 getRenderer()/makeNewRenderer()/clone(boolean)：
 * 无论 iText 从哪条路径创建渲染器，都拿到 {@link CellBorderEvent.BorderCellRenderer}。
 */
public class BorderedCell extends Cell {
    private final CellStyle style;
    private final CellStyle customStyle;

    public BorderedCell(int rowspan, int colspan, CellStyle style, CellStyle customStyle) {
        super(rowspan, colspan);
        this.style = style;
        this.customStyle = customStyle;
    }

    /**
     * iText 7.2.5 TableRenderer 处理跨行合并单元格跨页时通过本方法重建渲染器，必须返回自定义渲染器。
     */
    @Override
    public IRenderer getRenderer() {
        return new CellBorderEvent.BorderCellRenderer(this, style, customStyle);
    }

    @Override
    protected IRenderer makeNewRenderer() {
        return getRenderer();
    }

    /**
     * iText 7.2.5 Cell.clone(boolean) 硬编码 {@code new Cell(rowspan, colspan)}，返回普通 Cell，
     * 会丢掉子类与自定义渲染器。这里返回同类型的 BorderedCell，字段复制逻辑与官方保持一致
     * （row/col 为 Cell 私有字段，通过反射拷贝）。
     */
    @Override
    public Cell clone(boolean includeNonStandardProperties) {
        BorderedCell cloned = new BorderedCell(getRowspan(), getColspan(), style, customStyle);
        copyRowCol(cloned, getRow(), getCol());
        if (properties != null) {
            cloned.properties = new HashMap<Integer, Object>(properties);
        }
        if (styles != null) {
            cloned.styles = new LinkedHashSet<Style>(styles);
        }
        if (includeNonStandardProperties && childElements != null) {
            cloned.childElements = new ArrayList<IElement>(childElements);
        }
        return cloned;
    }

    private static void copyRowCol(Cell target, int row, int col) {
        try {
            Field rowField = Cell.class.getDeclaredField("row");
            rowField.setAccessible(true);
            rowField.setInt(target, row);
            Field colField = Cell.class.getDeclaredField("col");
            colField.setAccessible(true);
            colField.setInt(target, col);
        } catch (Exception ignored) {
            // row/col 只用于定位信息与 toString，复制失败不影响渲染
        }
    }
}
