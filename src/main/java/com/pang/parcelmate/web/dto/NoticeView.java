package com.pang.parcelmate.web.dto;

/** 一条站内提醒（红点里的小消息）。 */
public record NoticeView(
        long id,
        Long parcelId,
        String text,
        String at
) {
}
