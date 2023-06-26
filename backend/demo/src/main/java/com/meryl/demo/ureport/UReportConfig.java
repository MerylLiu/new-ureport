package com.meryl.demo.ureport;

import com.bstek.ureport.console.UReportServlet;
import com.bstek.ureport.definition.datasource.BuildinDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * @Author : Meryl
 * @Description:
 * @Date: Created in 2023/06/19
 */
@Configuration
@EnableAutoConfiguration
@Component
@ImportResource("classpath:ureport-console-context.xml")
public class UReportConfig implements BuildinDatasource {
    @Resource
    DataSource dataSource;
    private Logger log = LoggerFactory.getLogger(getClass());

    @Bean
    public ServletRegistrationBean buildUReportServlet() {
        return new ServletRegistrationBean(new UReportServlet(), "/ureport/*");
    }

    @Override
    public String name() {
        return "内置数据源";
    }

    @Override
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            log.error("UReport 数据源 获取连接失败！");
            e.printStackTrace();
        }
        return null;
    }
}
