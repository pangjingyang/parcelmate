package com.pang.parcelmate.domain;

import java.time.LocalDateTime;

/**
 * 一张快递单。
 *
 * <p>注意 {@code version}：这是乐观锁用的版本号。
 * 两个人同时点"接单"时，只有第一个 UPDATE 能匹配上 version，第二个会失败。
 */
public record Parcel(
        long id,
        long roomId,
        long ownerId,
        String courier,
        String station,
        String pickupCode,
        String reward,
        LocalDateTime deadline,
        ParcelStatus status,
        long version,
        Long takerId,
        LocalDateTime createdAt
) {
    public boolean isOpen() {
        return status == ParcelStatus.OPEN;
    }
}
