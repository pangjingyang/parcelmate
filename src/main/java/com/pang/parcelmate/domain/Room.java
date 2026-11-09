package com.pang.parcelmate.domain;

import java.time.LocalDateTime;

/** 宿舍（一个房间码对应一个宿舍）。 */
public record Room(
        long id,
        String code,
        String name,
        LocalDateTime createdAt
) {
}
