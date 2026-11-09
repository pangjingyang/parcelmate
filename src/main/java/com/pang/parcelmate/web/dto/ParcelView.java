package com.pang.parcelmate.web.dto;

/** 快递单的展示数据：把 owner_id / taker_id 换成昵称，前端好显示。 */
public record ParcelView(
        long id,
        String courier,
        String station,
        String pickupCode,
        String reward,
        String deadline,
        String status,
        String statusLabel,
        long version,
        Long ownerId,
        String ownerName,
        Long takerId,
        String takerName,
        String createdAt,
        boolean overdue
) {
}
