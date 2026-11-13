package com.zimo.gateway.user.mapper;

import com.zimo.gateway.user.entity.IpBanEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IP 封禁清单（tb_gw_ip_ban）
 */
@Mapper
public interface IpBanMapper {

    @Select("SELECT id, ip, reason, operator, status, banned_at AS bannedAt, unbanned_at AS unbannedAt"
            + " FROM tb_gw_ip_ban ORDER BY status DESC, banned_at DESC")
    List<IpBanEntity> selectAll();

    @Select("SELECT id, ip, reason, operator, status, banned_at AS bannedAt, unbanned_at AS unbannedAt"
            + " FROM tb_gw_ip_ban WHERE ip = #{ip} LIMIT 1")
    IpBanEntity selectByIp(@Param("ip") String ip);

    @Insert("INSERT INTO tb_gw_ip_ban(ip, reason, operator, status, banned_at)"
            + " VALUES(#{ip}, #{reason}, #{operator}, 1, #{bannedAt})"
            + " ON DUPLICATE KEY UPDATE reason = VALUES(reason), operator = VALUES(operator),"
            + " status = 1, banned_at = VALUES(banned_at), unbanned_at = NULL")
    int upsert(IpBanEntity entity);

    @Update("UPDATE tb_gw_ip_ban SET status = 0, unbanned_at = #{unbannedAt} WHERE ip = #{ip}")
    int unban(@Param("ip") String ip, @Param("unbannedAt") LocalDateTime unbannedAt);

    @Select("SELECT COUNT(*) FROM tb_gw_ip_ban WHERE status = 1")
    int countActive();
}
