package com.pang.parcelmate.domain;

import java.time.LocalDateTime;

/** 站内提醒（红点）。一期不做微信/QQ 推送，先在自己的页面上提示。 */
public record Notice(
        long id,
        long memberId,
        Long parcelId,
        String type,
        boolean isRead,
        LocalDateTime at
) {
}
