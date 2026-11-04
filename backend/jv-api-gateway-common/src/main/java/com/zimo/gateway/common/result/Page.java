package com.zimo.gateway.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果（服务层内部使用，替代对 ORM 分页插件的依赖）。
 *
 * <p>分页由各 Mapper 用 LIMIT / OFFSET 手写实现，
 * total 用同一 WHERE 条件的 COUNT 查询得出，行为与分页插件一致。
 *
 * @param <T> 记录类型
 */
@Data
public class Page<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long pages;
    private long current;
    private long size;

    public static <T> Page<T> of(List<T> records, long total, long current, long size) {
        Page<T> page = new Page<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setCurrent(current);
        page.setSize(size);
        // 向上取整，与 MyBatis-Plus 分页插件的口径一致
        page.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        return page;
    }
}
