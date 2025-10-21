package com.meryl.demo.ureport;

import cn.hutool.core.util.StrUtil;

import java.util.HashMap;
import java.util.Map;

public class MapUtil {
    public static Map toPascal(Map source) {
        Map res = new HashMap();
        source.forEach((k, v) -> res.put(StrUtil.toUnderlineCase(k.toString()), v));
        return res;
    }

    public static Map toCamel(Map source) {
        Map res = new HashMap();
        source.forEach((k, v) -> res.put(StrUtil.toCamelCase(k.toString()), v));
        return res;
    }
}
