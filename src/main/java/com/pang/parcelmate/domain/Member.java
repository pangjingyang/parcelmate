package com.pang.parcelmate.domain;

import java.time.LocalDateTime;

/** 宿舍成员：昵称 + 加入时间，不做账号密码。 */
public record Member(
        long id,
        long roomId,
        String nickname,
        LocalDateTime joinAt,
        LocalDateTime lastActive
) {
}
