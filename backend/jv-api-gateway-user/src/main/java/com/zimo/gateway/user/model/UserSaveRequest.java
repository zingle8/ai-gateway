package com.zimo.gateway.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增 / 修改用户请求
 */
@Data
public class UserSaveRequest {

    @NotBlank(message = "账号不能为空")
    private String username;

    /** 新增时必填；修改时留空表示不修改密码 */
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String nickname;

    private String email;

    private String phone;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
