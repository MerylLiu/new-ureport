/*******************************************************************************
* Copyright 2017 Bstek
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License.  You may obtain a copy of
* the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
* License for the specific language governing permissions and limitations under
* the License.
******************************************************************************/
package com.bstek.ureport.export.pdf.font;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.bstek.ureport.exception.ReportComputeException;
import com.bstek.ureport.exception.ReportException;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;

/**
 * @author Jacky.gao
 * @since 2014年4月22日
 */
public class FontBuilder implements ApplicationContextAware {
    private static ApplicationContext applicationContext;
    /**
     * 字体程序缓存（iText 7 中 FontProgram 是可跨文档共享的只读数据）。
     * iText 7 的 PdfFont 不可跨文档复用（内部持有属于某个 PdfDocument 的间接对象），
     * 因此这里只缓存 FontProgram，PdfFont 通过 ThreadLocal 按文档生命周期缓存。
     */
    private static final Map<String, FontProgram> fontProgramMap = new HashMap<String, FontProgram>();
    /**
     * 每个线程（即每次 PDF 导出）的 PdfFont 缓存。
     * PdfProducer 在导出结束时调用 {@link #clearThreadFontCache()} 清理。
     */
    private static final ThreadLocal<Map<String, PdfFont>> threadFontMap = new ThreadLocal<Map<String, PdfFont>>();
    public static final Map<String, String> fontPathMap = new HashMap<String, String>();
    private static List<String> systemFontNameList = new ArrayList<String>();

    /**
     * 获取 PdfFont。返回的字体不可修改样式（iText7 中字体与样式是分开管理的），
     * 因此保留了原始 getFont 签名，但返回值改为 PdfFont。
     * 同一线程内返回同一实例，避免同一文档中重复嵌入字体；跨文档（跨线程或清理后）会重新创建。
     */
    public static PdfFont getFont(String fontName, int fontSize, boolean fontBold, boolean fontItalic, boolean underLine) {
        Map<String, PdfFont> cache = threadFontMap.get();
        if (cache == null) {
            cache = new HashMap<String, PdfFont>();
            threadFontMap.set(cache);
        }
        PdfFont font = cache.get(fontName);
        if (font != null) {
            return font;
        }
        FontProgram program = fontProgramMap.get(fontName);
        if (program != null) {
            // createFont(FontProgram,...) 无受检异常
            font = PdfFontFactory.createFont(program, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
            cache.put(fontName, font);
            return font;
        }
        // 未注册的字体名：依次尝试 iText 内置字体、CJK 内置字体、Helvetica
        try {
            font = PdfFontFactory.createFont(fontName, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception e) {
            try {
                font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
            } catch (Exception ex) {
                try {
                    font = PdfFontFactory.createFont("Helvetica");
                } catch (Exception exc) {
                    throw new ReportComputeException(exc);
                }
            }
        }
        cache.put(fontName, font);
        return font;
    }

    /**
     * 清理当前线程的 PdfFont 缓存。必须在每次 PDF 文档导出结束后调用，
     * 否则缓存的 PdfFont 属于已关闭的 PdfDocument，再次使用会抛出
     * "Pdf indirect object belongs to other PDF document"。
     */
    public static void clearThreadFontCache() {
        threadFontMap.remove();
    }

    public static java.awt.Font getAwtFont(String fontName, int fontStyle, float size) {
        if (systemFontNameList.contains(fontName)) {
            return new java.awt.Font(fontName, fontStyle, new Float(size).intValue());
        }
        String fontPath = fontPathMap.get(fontName);
        if (fontPath == null) {
            fontName = "宋体";
            fontPath = fontPathMap.get(fontName);
            if (fontPath == null) {
                return null;
            }
        }
        InputStream inputStream = null;
        try {
            inputStream = applicationContext.getResource(fontPath).getInputStream();
            java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, inputStream);
            return font.deriveFont(fontStyle, size);
        } catch (Exception e) {
            throw new ReportException(e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        FontBuilder.applicationContext = applicationContext;
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = environment.getAvailableFontFamilyNames();
        for (String name : fontNames) {
            systemFontNameList.add(name);
        }
        Collection<FontRegister> fontRegisters = applicationContext.getBeansOfType(FontRegister.class).values();
        for (FontRegister fontReg : fontRegisters) {
            String fontName = fontReg.getFontName();
            String fontPath = fontReg.getFontPath();
            if (StringUtils.isEmpty(fontPath) || StringUtils.isEmpty(fontName)) {
                continue;
            }
            try {
                FontProgram fontProgram = getIdentityFontProgram(fontName, fontPath, applicationContext);
                if (fontProgram == null) {
                    throw new ReportComputeException("Font " + fontPath + " does not exist");
                }
                fontProgramMap.put(fontName, fontProgram);
            } catch (Exception e) {
                e.printStackTrace();
                throw new ReportComputeException(e);
            }
        }
    }

    /**
     * 加载字体文件并返回 FontProgram。
     * 注意：TTC（TrueType Collection，如 SIMSUN.TTC / msyh.ttc）必须通过
     * FontProgramFactory.createFont(bytes, index, false) 指定字体索引加载，
     * 直接使用 PdfFontFactory.createFont(byte[]) 会抛出
     * "Type of font is not recognized"。
     */
    private FontProgram getIdentityFontProgram(String fontFamily, String fontPath, ApplicationContext applicationContext) throws IOException {
        if (!fontPath.startsWith(ApplicationContext.CLASSPATH_URL_PREFIX)) {
            fontPath = ApplicationContext.CLASSPATH_URL_PREFIX + fontPath;
        }
        InputStream inputStream = null;
        try {
            fontPathMap.put(fontFamily, fontPath);
            inputStream = applicationContext.getResource(fontPath).getInputStream();
            byte[] bytes = IOUtils.toByteArray(inputStream);
            String lowerPath = fontPath.toLowerCase();
            if (lowerPath.endsWith(".ttc")) {
                // TTC 字体集合：取第一个 face（与 iText5 的 "xxx.ttc,0" 行为一致）
                return FontProgramFactory.createFont(bytes, 0, false);
            }
            return FontProgramFactory.createFont(bytes);
        } finally {
            if (inputStream != null) inputStream.close();
        }
    }
}
