package com.pang.parcelmate.web;

import com.pang.parcelmate.domain.Member;
import com.pang.parcelmate.domain.Room;
import com.pang.parcelmate.service.RoomService;
import com.pang.parcelmate.web.dto.MeView;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宿舍相关的 HTTP 接口。
 *
 * <p>@RestController = 这个类的返回值直接变成 JSON 响应。
 * Controller 只做三件事：取参数 → 调 Service → 写响应，不写业务规则。
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    /** 身份 cookie 名：里面存 member 的主键。 */
    public static final String COOKIE_MEMBER = "pm_member";

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /** 建房：POST /api/rooms  {"name":"..."} */
    @PostMapping("/rooms")
    public Room createRoom(@RequestBody CreateRoomRequest req) {
        return roomService.createRoom(req.name());
    }

    /** 加入宿舍：POST /api/rooms/join  {"code":"AB12CD","nickname":"小明"} */
    @PostMapping("/rooms/join")
    public MeView join(@RequestBody JoinRequest req, HttpServletResponse response) {
        Member member = roomService.join(req.code(), req.nickname());
        // 把 memberId 写进 cookie，之后每个请求都带着它 —— 这就是我们的"轻登录"
        Cookie cookie = new Cookie(COOKIE_MEMBER, String.valueOf(member.id()));
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 30);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return roomService.me(member.id());
    }

    /** 当前身份：GET /api/me —— 没加入过会返回 joined=false */
    @GetMapping("/me")
    public MeView me(@CookieValue(name = COOKIE_MEMBER, required = false) Long memberId) {
        if (memberId == null || roomService.findMemberOrNull(memberId) == null) {
            return MeView.notJoined();
        }
        return roomService.me(memberId);
    }

    public record CreateRoomRequest(String name) {
    }

    public record JoinRequest(String code, String nickname) {
    }
}
