package com.bstek.ureport.font.fzkt;

import com.bstek.ureport.export.pdf.font.FontRegister;


/**
 * @author Jacky.gao
 * @since 2014年5月7日
 */
public class FzktFontRegister implements FontRegister {

    public String getFontName() {
        return "方正楷体";
    }

    public String getFontPath() {
        return "com/bstek/ureport/font/fzkt/Fzkt.TTF";
    }
}
