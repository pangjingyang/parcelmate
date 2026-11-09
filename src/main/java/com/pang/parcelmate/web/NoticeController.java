package com.pang.parcelmate.web;

import com.pang.parcelmate.domain.Member;
import com.pang.parcelmate.service.BizException;
import com.pang.parcelmate.service.NoticeService;
import com.pang.parcelmate.service.RoomService;
import com.pang.parcelmate.web.dto.NoticeView;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 站内提醒（红点）接口。 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;
    private final RoomService roomService;

    public NoticeController(NoticeService noticeService, RoomService roomService) {
        this.noticeService = noticeService;
        this.roomService = roomService;
    }

    @GetMapping
    public List<NoticeView> list(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId) {
        return noticeService.listUnread(requireMember(memberId));
    }

    @PostMapping("/read")
    public Map<String, Object> readAll(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId) {
        noticeService.markAllRead(requireMember(memberId));
        return Map.of("ok", true);
    }

    private Member requireMember(Long memberId) {
        Member member = roomService.findMemberOrNull(memberId);
        if (member == null) {
            throw new BizException("请先加入宿舍");
        }
        return member;
    }
}
