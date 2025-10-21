package com.bstek.ureport.font;

import org.springframework.core.io.ClassPathResource;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ExtendFonts {

    public static List<String> getFontNames() {
        List fontList = new ArrayList();

        ClassLoader classLoader = new ExtendFonts().getClass().getClassLoader();
        URL resourceURL = classLoader.getResource("reportFonts");
        String filePath = resourceURL.getPath();

        ClassPathResource reportFonts = new ClassPathResource("reportFonts");
        String path = reportFonts.getPath();
        File[] fileList = new File(filePath).listFiles();
        for (File f : fileList) {
            Font f2;
            try {
                f2 = Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(f.getAbsoluteFile()));
                String fontName = f2.getName();

                System.out.println(f.getName() + "  ---> 字体名:" + fontName);
                fontList.add(fontName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return fontList;
    }
}
