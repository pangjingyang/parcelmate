package com.pang.parcelmate.web;

import com.pang.parcelmate.domain.Member;
import com.pang.parcelmate.service.BizException;
import com.pang.parcelmate.service.ParcelService;
import com.pang.parcelmate.service.RoomService;
import com.pang.parcelmate.web.dto.ParcelView;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 快递单相关的 HTTP 接口。 */
@RestController
@RequestMapping("/api/parcels")
public class ParcelController {

    private final ParcelService parcelService;
    private final RoomService roomService;

    public ParcelController(ParcelService parcelService, RoomService roomService) {
        this.parcelService = parcelService;
        this.roomService = roomService;
    }

    /** 发布快递单。 */
    @PostMapping
    public ParcelView create(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId,
            @RequestBody ParcelService.CreateParcelCommand cmd) {
        return parcelService.create(requireMember(memberId), cmd);
    }

    /** 宿舍里进行中的单子。 */
    @GetMapping
    public List<ParcelView> listActive(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId) {
        return parcelService.listActive(requireMember(memberId));
    }

    /** 宿舍全部单子（含历史）。 */
    @GetMapping("/history")
    public List<ParcelView> history(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId) {
        return parcelService.listHistory(requireMember(memberId));
    }

    /** 我发布的 / 我接的。 */
    @GetMapping("/mine")
    public List<ParcelView> mine(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId,
            @RequestParam(defaultValue = "false") boolean asTaker) {
        return parcelService.listMine(requireMember(memberId), asTaker);
    }

    @PostMapping("/{id}/take")
    public ParcelView take(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId,
            @PathVariable long id) {
        return parcelService.take(requireMember(memberId), id);
    }

    @PostMapping("/{id}/pick")
    public ParcelView pick(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId,
            @PathVariable long id) {
        return parcelService.pick(requireMember(memberId), id);
    }

    @PostMapping("/{id}/deliver")
    public ParcelView deliver(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId,
            @PathVariable long id) {
        return parcelService.deliver(requireMember(memberId), id);
    }

    @PostMapping("/{id}/cancel")
    public ParcelView cancel(
            @CookieValue(name = RoomController.COOKIE_MEMBER, required = false) Long memberId,
            @PathVariable long id) {
        return parcelService.cancel(requireMember(memberId), id);
    }

    /** cookie 里的身份必须真实存在，否则让前端去加入页面。 */
    private Member requireMember(Long memberId) {
        Member member = roomService.findMemberOrNull(memberId);
        if (member == null) {
            throw new BizException("请先加入宿舍");
        }
        return member;
    }
}
