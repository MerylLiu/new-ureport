package com.meryl.demo.util;

import com.bstek.ureport.Utils;
import com.bstek.ureport.export.ExportConfigureImpl;
import com.bstek.ureport.export.ExportManager;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * @Author : Meryl
 * @Description:
 * @Date: Created in 2024/9/7 13:05
 */
public class PrintUtil {
    public static byte[] getBytes(Map params, String reportFileName) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExportManager exportManager = (ExportManager) Utils.getApplicationContext().getBean(ExportManager.BEAN_ID);
        exportManager.exportPdf(new ExportConfigureImpl(reportFileName, params, output));
        return output.toByteArray();
    }

    public static byte[] getExcelBytes(Map params, String reportFileName) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExportManager exportManager = (ExportManager) Utils.getApplicationContext().getBean(ExportManager.BEAN_ID);
        exportManager.exportExcel(new ExportConfigureImpl(reportFileName, params, output));
        return output.toByteArray();
    }
}
