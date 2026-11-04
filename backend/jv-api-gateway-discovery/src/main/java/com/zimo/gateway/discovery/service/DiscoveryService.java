package com.zimo.gateway.discovery.service;

import com.zimo.gateway.client.common.dto.ServiceInstanceDTO;
import com.zimo.gateway.common.result.Page;
import com.zimo.gateway.discovery.entity.GwService;
import com.zimo.gateway.discovery.model.ServiceQuery;

import java.util.Map;

/**
 * 服务发现服务
 */
public interface DiscoveryService {

    /** 按主键查询（逻辑删除的不返回） */
    GwService getById(Long id);

    /**
     * 服务分页列表（关键字 / 应用 / 服务名 / 状态 / 语言过滤）
     */
    Page<GwService> pageServices(ServiceQuery query);

    /**
     * 服务详情
     */
    GwService getDetail(Long id);

    /**
     * 筛选项字典：应用（appId + appName）、服务（appId + serviceName）、语言类型，
     * 均取自库中真实出现过的值，供页面「全部应用 / 全部服务」下拉使用。
     */
    Map<String, Object> filterOptions();

    /**
     * 服务实例注册 / 更新（自动接入）
     */
    void register(ServiceInstanceDTO instance);

    /**
     * 服务心跳
     */
    void heartbeat(ServiceInstanceDTO instance);

    /**
     * 服务实例下线
     */
    void deregister(ServiceInstanceDTO instance);
}
