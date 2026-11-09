package com.pang.parcelmate.web.dto;

import com.pang.parcelmate.domain.Room;

import java.util.List;

/** "我是谁 + 我在哪个宿舍"：前端打开页面第一个请求就拿它。 */
public record MeView(
        boolean joined,
        Long memberId,
        String nickname,
        Room room,
        List<MemberView> members,
        int unreadCount
) {
    public static MeView notJoined() {
        return new MeView(false, null, null, null, List.of(), 0);
    }
}
