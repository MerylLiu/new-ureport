package com.meryl.demo.ureport;

import com.bstek.ureport.provider.image.ImageProvider;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @Author : Meryl
 * @Description:
 * @Date: Created in 2023/06/19
 */
@Component
public class UReportImageProvider implements ImageProvider, ApplicationContextAware {

    @Override
    public InputStream getImage(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5 * 1000);
                InputStream inputStream = conn.getInputStream();
                return inputStream;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public boolean support(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return true;
        }
        return false;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

    }
}
