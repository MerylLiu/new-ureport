package com.meryl.demo.controller;

import com.extm.Db;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

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
}
