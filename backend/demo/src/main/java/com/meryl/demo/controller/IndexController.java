package com.meryl.demo.controller;

import com.bstek.ureport.exception.ReportException;
import com.extm.Db;
import com.meryl.demo.util.PrintUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

/**
 * @Author : Meryl
 * @Description:
 * @Date: Created in 2019/8/28
 * @Modify by :
 */
@RestController
public class IndexController {

    @RequestMapping("/")
    @ResponseBody
    public List index() {
        List data = Db.session("tmpl_report").select();
        return data;
    }

    @RequestMapping("/test")
    public void test(String file, HttpServletResponse response) {
        try {
            byte[] output = PrintUtil.getBytes(new HashMap<>(), file);

            OutputStream out = response.getOutputStream();
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/pdf; charset=UTF-8");
            String downFileName = "test.pdf";
            response.setHeader("Content-Disposition", "inline; filename=" + URLEncoder.encode(downFileName, "UTF-8"));
            response.setHeader("code", "20000");
            out.write(output);
        } catch (Exception ex) {
            throw new ReportException(ex);
        }
    }
}
