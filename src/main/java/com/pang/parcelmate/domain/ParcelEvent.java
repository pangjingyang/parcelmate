package com.pang.parcelmate.domain;

import java.time.LocalDateTime;

/**
 * 快递单的操作流水：谁在什么时候做了什么。
 * 状态可以变，但流水只追加不修改——出问题时能复盘。
 */
public record ParcelEvent(
        long id,
        long parcelId,
        String action,
        Long byMember,
        LocalDateTime at
) {
}
