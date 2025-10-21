package com.meryl.demo.ureport;

import cn.hutool.core.date.DateUtil;
import com.bstek.ureport.provider.report.ReportFile;
import com.bstek.ureport.provider.report.ReportProvider;
import com.extm.Db;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * @Author : Meryl
 * @Description:
 * @Date: Created in 2019/10/21
 * @Modify by :
 */
@Component
public class DbReportProvider implements ReportProvider {
    private static final String NAME = "数据库";
    private String prefix = "rc:";
    private boolean disable = false;

    @Override
    public InputStream loadReport(String fileName) {
        Map map = new HashMap();
        map.put("fileName", getNoCorrectName(fileName));
        Map data = Db.table("tmpl_report").find("file_name = #{fileName}", map);

        byte[] res = data.get("content").toString().getBytes();

        InputStream stream = new ByteArrayInputStream(res);
        return stream;
    }

    @Override
    public void deleteReport(String fileName) {
        if (fileName != null) {
            fileName = getNoCorrectName(fileName);

            Map map = new HashMap();
            map.put("fileName", fileName);
            Db.table("tmpl_report").delete("file_name = #{fileName}", map);
        }
    }

    @Override
    public List<ReportFile> getReportFiles() {
        List<Map> list = Db.table("tmpl_report").orderBy("create_time desc").select();

        List<ReportFile> reportFiles = new ArrayList();
        for (Map item : list) {
            LocalDateTime dateTime = (LocalDateTime) (item.get("update_time") != null ? item.get("update_time") : item.get("create_time"));
            Date date = Date.from(dateTime.toInstant(ZoneOffset.ofHours(8)));
            reportFiles.add(new ReportFile(item.get("file_name").toString(), date));
        }
        return reportFiles;
    }

    @Override
    public void saveReport(String fileName, String content) {
        fileName = getNoCorrectName(fileName);

        Map map = new HashMap();
        map.put("fileName", fileName);
        Map data = Db.table("tmpl_report").find("file_name = #{fileName}", map);

        Map param = new HashMap();
        if (data == null) {
            param.put("name", fileName.substring(0, fileName.indexOf(".")));
            param.put("fileName", fileName);
            param.put("content", content);
            param.put("createTime", DateUtil.now());
            param.put("updateTime", DateUtil.now());
            param.put("previewPath", "/ureport/preview?_u=rc:" + fileName + "&_i=1&");
            Db.table("tmpl_report").insert(MapUtil.toPascal(param));
        } else {
            param.put("name", fileName.substring(0, fileName.indexOf(".")));
            param.put("fileName", fileName);
            param.put("content", content);
            param.put("updateTime", DateUtil.now());
            param.put("previewPath", "/ureport/preview?_u=rc:" + fileName + "&_i=1&");
            Db.table("tmpl_report").where("id = #{id}", new HashMap() {{
                put("id", data.get("id"));
            }}).update(MapUtil.toPascal(param));
        }
    }

    /**
     * 获取没有前缀的文件名
     *
     * @param name
     * @return
     */
    private String getNoCorrectName(String name) {
        if (name.startsWith(prefix)) {
            name = name.substring(prefix.length());
        }
        return name;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean disabled() {
        return disable;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }
}
