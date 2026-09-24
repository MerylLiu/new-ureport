package com.bstek.ureport;

import com.itextpdf.io.font.otf.Glyph;
import com.itextpdf.io.font.otf.GlyphLine;
import com.itextpdf.layout.splitting.DefaultSplitCharacters;
import com.itextpdf.layout.splitting.ISplitCharacters;

public class ChineseSplitCharacter extends DefaultSplitCharacters implements ISplitCharacters  {
    // 禁止在行首出现的标点（句末、右括号、右引号）
    private static final String FORBIDDEN_START = "，。、；：？！）】》」』〕】”’》〉〕";
    // 禁止在行尾出现的标点（句首、左括号、左引号）
    private static final String FORBIDDEN_END = "（【《「『〔{“‘〈";

    @Override
    public boolean isSplitCharacter(GlyphLine text, int glyphPos) {
        // 先调用父类默认判断（空格、换行、连字符等基础分割）
        boolean baseCanSplit = super.isSplitCharacter(text, glyphPos);

        int len = text.size();
        Glyph currentGlyph = text.get(glyphPos);
        char curr = (char) currentGlyph.getUnicode();

        // 边界保护
        int nextPos = glyphPos + 1;
        char next = nextPos < len ? (char) text.get(nextPos).getUnicode() : 0;
        int prevPos = glyphPos - 1;
        char prev = prevPos >= 0 ? (char) text.get(prevPos).getUnicode() : 0;

        // 规则1：下一个字符是【行首禁标点】→ 当前位置禁止换行（防止标点跑到下一行开头）
        if (FORBIDDEN_START.indexOf(next) != -1) {
            return false;
        }

        // 规则2：当前字符是【行尾禁标点】→ 当前位置强制允许换行（左括号不能留在行末尾）
        if (FORBIDDEN_END.indexOf(curr) != -1) {
            return true;
        }

        // 规则3：中、日、韩文字，任意位置都可以换行
        if (isChineseCJK(curr)) {
            return true;
        }

        // 基础规则生效（英文空格、换行符、连字符）
        return baseCanSplit;
    }

    /**
     * 判断是否为CJK中日韩统一表意文字（汉字）
     */
    private boolean isChineseCJK(char c) {
        // 汉字范围：0x4E00 ~ 0x9FA5
        return (c >= 0x4E00 && c <= 0x9FA5)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0x20000 && c <= 0x2A6DF);
    }
}
