package com.meryl.demo.config;

import com.extm.SqlSessionFactoryBeanEx;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import javax.sql.DataSource;

/**
 * @Author : Meryl
 * @Description:
 * @Date: Created in 2019/8/28
 * @Modify by :
 */
@Configuration
public class MyBatisConfig {
    @javax.annotation.Resource
    DataSource dataSource;

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBeanEx();
        sqlSessionFactoryBean.setDataSource(dataSource);

        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
//            Resource[] mybatisConfigXml = resolver.getResources("classpath*:com/**/cfg/mybatis.cfg.xml");
//            sqlSessionFactoryBean.setConfigLocation(mybatisConfigXml[mybatisConfigXml.length]);

            Resource[] mybatisMapperXml = resolver.getResources("classpath*:com/**/sql/*.xml");
            sqlSessionFactoryBean.setMapperLocations(mybatisMapperXml);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sqlSessionFactoryBean;
    }
}
