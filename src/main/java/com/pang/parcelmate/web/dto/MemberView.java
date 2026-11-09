package com.pang.parcelmate.web.dto;

/** 成员的展示数据：加一个 online 字段，前端不用自己算时间差。 */
public record MemberView(
        long id,
        String nickname,
        boolean online,
        String lastActiveText
) {
}
