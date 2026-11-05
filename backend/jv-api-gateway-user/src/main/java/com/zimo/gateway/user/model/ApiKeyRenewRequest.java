package com.zimo.gateway.user.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * API Key 续期请求
 *
 * @param renewDays 延长天数（1~3650）
 *                  <ul>
 *                    <li>未过期：在原到期时间上顺延</li>
 *                    <li>已过期：从当前时间起算，续期后立即恢复可用</li>
 *                  </ul>
 */
public record ApiKeyRenewRequest(
        @NotNull(message = "续期天数不能为空")
        @Min(value = 1, message = "续期天数至少 1 天")
        @Max(value = 3650, message = "续期天数最多 3650 天")
        Integer renewDays) {
}
