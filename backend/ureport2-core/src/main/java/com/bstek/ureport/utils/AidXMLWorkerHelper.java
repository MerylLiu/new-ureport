package com.bstek.ureport.utils;

import com.bstek.ureport.export.pdf.CellPhrase;
import com.itextpdf.text.Font;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.XMLWorker;
import com.itextpdf.tool.xml.XMLWorkerFontProvider;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import com.itextpdf.tool.xml.css.CssFile;
import com.itextpdf.tool.xml.css.StyleAttrCSSResolver;
import com.itextpdf.tool.xml.html.CssAppliers;
import com.itextpdf.tool.xml.html.CssAppliersImpl;
import com.itextpdf.tool.xml.html.Tags;
import com.itextpdf.tool.xml.parser.XMLParser;
import com.itextpdf.tool.xml.pipeline.css.CSSResolver;
import com.itextpdf.tool.xml.pipeline.css.CssResolverPipeline;
import com.itextpdf.tool.xml.pipeline.end.ElementHandlerPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipeline;
import com.itextpdf.tool.xml.pipeline.html.HtmlPipelineContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class AidXMLWorkerHelper {

    public static class AidFontsProvider extends XMLWorkerFontProvider {
        private Font _font;

        public AidFontsProvider(Font font) {
            _font = font;
        }

        @Override
        public Font getFont(String fontName, String encoding, float size, final int style) {
            if (_font != null) {
                return _font;
            }
            if (fontName == null) {
                fontName = "宋体";
            }
            return super.getFont(fontName, encoding, size, style);
        }
    }

    public static ElementList parseToElementList(String html, String css, Font font) throws IOException {
        // CSS
        CSSResolver cssResolver = new StyleAttrCSSResolver();
        if (css != null) {
            CssFile cssFile = XMLWorkerHelper.getCSS(new ByteArrayInputStream(css.getBytes()));
            cssResolver.addCss(cssFile);
        }

        // HTML
        AidFontsProvider fontProvider = new AidFontsProvider(font);
        CssAppliers cssAppliers = new CssAppliersImpl(fontProvider);
        HtmlPipelineContext htmlContext = new HtmlPipelineContext(cssAppliers);
        htmlContext.setTagFactory(Tags.getHtmlTagProcessorFactory());
        htmlContext.autoBookmark(false);

        // Pipelines
        ElementList elements = new ElementList();
        ElementHandlerPipeline end = new ElementHandlerPipeline(elements, null);
        HtmlPipeline htmlPipeline = new HtmlPipeline(htmlContext, end);
        CssResolverPipeline cssPipeline = new CssResolverPipeline(cssResolver, htmlPipeline);

        // XML Worker
        XMLWorker worker = new XMLWorker(cssPipeline, true);
        XMLParser p = new XMLParser(worker);
        html = html.replace("<br>", "").replace("<hr>", "").replace("<img>", "").replace("<param>", "")
                .replace("<link>", "").replace("></", "> </");
        p.parse(new ByteArrayInputStream(html.getBytes()));
        return elements;
    }
}