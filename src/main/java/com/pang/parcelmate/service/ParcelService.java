package com.pang.parcelmate.service;

import com.pang.parcelmate.domain.Member;
import com.pang.parcelmate.domain.Parcel;
import com.pang.parcelmate.domain.ParcelStatus;
import com.pang.parcelmate.repo.MemberRepository;
import com.pang.parcelmate.repo.NoticeRepository;
import com.pang.parcelmate.repo.ParcelEventRepository;
import com.pang.parcelmate.repo.ParcelRepository;
import com.pang.parcelmate.web.dto.ParcelView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 快递单的业务逻辑。
 *
 * <p>M2（登记 + 列表）这里已经写好了，你可以照着读。
 * <p>M3（接单 / 已取 / 已送达）留给你写，方法体里写着要做哪几步 —— 见 W2 任务。
 */
@Service
public class ParcelService {

    private static final DateTimeFormatter TEXT_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter INPUT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final ParcelRepository parcelRepo;
    private final ParcelEventRepository eventRepo;
    private final MemberRepository memberRepo;
    private final NoticeRepository noticeRepo;

    public ParcelService(ParcelRepository parcelRepo, ParcelEventRepository eventRepo,
                         MemberRepository memberRepo, NoticeRepository noticeRepo) {
        this.parcelRepo = parcelRepo;
        this.eventRepo = eventRepo;
        this.memberRepo = memberRepo;
        this.noticeRepo = noticeRepo;
    }

    /** 发布一张快递单。 */
    public ParcelView create(Member me, CreateParcelCommand cmd) {
        String courier = require(cmd.courier(), "快递公司不能为空");
        String station = require(cmd.station(), "取件点不能为空");
        if (courier.length() > 20 || station.length() > 20) {
            throw new BizException("快递公司 / 取件点太长了");
        }
        String pickupCode = cmd.pickupCode() == null ? null : cmd.pickupCode().trim();
        if (pickupCode != null && pickupCode.length() > 40) {
            throw new BizException("取件码太长了");
        }
        LocalDateTime deadline = parseDeadline(cmd.deadline());

        long id = parcelRepo.insert(me.roomId(), me.id(), courier, station,
                emptyToNull(pickupCode), emptyToNull(cmd.reward()), deadline);
        eventRepo.append(id, "CREATED", me.id());
        return toViews(me.roomId(), List.of(parcelRepo.findById(id).orElseThrow()), me.id()).get(0);
    }

    /** 宿舍里还在进行中的单子。 */
    public List<ParcelView> listActive(Member me) {
        return toViews(me.roomId(), parcelRepo.listActiveByRoom(me.roomId()), me.id());
    }

    /** 宿舍全部单子（含历史）。 */
    public List<ParcelView> listHistory(Member me) {
        return toViews(me.roomId(), parcelRepo.listAllByRoom(me.roomId()), me.id());
    }

    /** 我发布的 / 我接的（M4 用，先把数据查出来）。 */
    public List<ParcelView> listMine(Member me, boolean asTaker) {
        List<Parcel> list = asTaker
                ? parcelRepo.listByTaker(me.id())
                : parcelRepo.listByOwner(me.id());
        return toViews(me.roomId(), list, me.id());
    }

    // ======================= W2：这一周由你来写 =======================

    /**
     * 接单：把一张 OPEN 的单子抢下来。
     *
     * <p>要写的步骤（照着做，不要跳）：
     * <ol>
     *   <li>校验 me 不是发布者本人 —— 自己的快递不能自己接</li>
     *   <li>执行 {@link ParcelRepository#takeIfOpen}，拿返回值</li>
     *   <li>返回 0：说明被人抢先或状态已变 → 抛 BizException("手慢了，这单已经被接走了")</li>
     *   <li>返回 1：写一条 parcel_event（action = "TAKEN"），给发布者插一条 notice</li>
     * </ol>
     * 关键点：**不要**先 SELECT 查状态再 UPDATE。先查再改，中间那几毫秒就可能被人插队，
     * 这正是版本号要解决的问题（W2 会画时间线讲）。
     */
    public ParcelView take(Member me, long parcelId) {
        Parcel parcel = load(parcelId, me);
        if (parcel.ownerId() == me.id()) {
            throw new BizException("自己的快递不能自己接");
        }
        if (parcel.status() != ParcelStatus.OPEN) {
            throw new BizException("这单已经是「" + parcel.status().getLabel() + "」，接不了了");
        }

        // 关键：一条 SQL 完成「检查 + 抢占」，靠受影响行数判断谁赢了
        int updated = parcelRepo.takeIfOpen(parcelId, parcel.version(), me.id());
        if (updated == 0) {
            throw new BizException("手慢了，这单刚被别人接走");
        }

        eventRepo.append(parcelId, "TAKEN", me.id());
        noticeRepo.insert(parcel.ownerId(), parcelId, "TAKEN");
        return view(parcelId, me);
    }

    /** 已取：接单人从驿站拿到包裹。W2。 */
    public ParcelView pick(Member me, long parcelId) {
        Parcel parcel = load(parcelId, me);
        if (parcel.takerId() == null || parcel.takerId() != me.id()) {
            throw new BizException("只有接单人能确认「已取到」");
        }
        if (parcel.status() != ParcelStatus.TAKEN) {
            throw new BizException("当前是「" + parcel.status().getLabel() + "」，不能确认已取");
        }
        int updated = parcelRepo.transit(parcelId, ParcelStatus.TAKEN, ParcelStatus.PICKED, parcel.version());
        if (updated == 0) {
            throw new BizException("状态刚变过，刷新一下再试");
        }
        eventRepo.append(parcelId, "PICKED", me.id());
        noticeRepo.insert(parcel.ownerId(), parcelId, "PICKED");
        return view(parcelId, me);
    }

    /** 已送达：包裹交到发布者手里。W2。 */
    public ParcelView deliver(Member me, long parcelId) {
        Parcel parcel = load(parcelId, me);
        if (parcel.ownerId() != me.id()) {
            throw new BizException("只有发布者能确认「已收到」");
        }
        if (parcel.status() != ParcelStatus.PICKED) {
            throw new BizException("当前是「" + parcel.status().getLabel() + "」，不能确认收货");
        }
        int updated = parcelRepo.transit(parcelId, ParcelStatus.PICKED, ParcelStatus.DELIVERED, parcel.version());
        if (updated == 0) {
            throw new BizException("状态刚变过，刷新一下再试");
        }
        eventRepo.append(parcelId, "DELIVERED", me.id());
        if (parcel.takerId() != null) {
            noticeRepo.insert(parcel.takerId(), parcelId, "DELIVERED");
        }
        return view(parcelId, me);
    }

    /** 取消：只有发布者能取消，且只能取消还没被接的单。W2。 */
    public ParcelView cancel(Member me, long parcelId) {
        Parcel parcel = load(parcelId, me);
        if (parcel.ownerId() != me.id()) {
            throw new BizException("只有发布者能取消这张单");
        }
        if (parcel.status() != ParcelStatus.OPEN) {
            throw new BizException("已经有人接了，不能直接取消");
        }
        int updated = parcelRepo.transit(parcelId, ParcelStatus.OPEN, ParcelStatus.CANCELED, parcel.version());
        if (updated == 0) {
            throw new BizException("状态刚变过，刷新一下再试");
        }
        eventRepo.append(parcelId, "CANCELED", me.id());
        return view(parcelId, me);
    }

    // ========================= 下面是工具方法 =========================

    /** 取出单子并确认它属于我这个宿舍，否则别人猜 id 就能操作别的宿舍。 */
    private Parcel load(long parcelId, Member me) {
        Parcel parcel = parcelRepo.findById(parcelId)
                .orElseThrow(() -> new BizException("没有这张单子"));
        if (parcel.roomId() != me.roomId()) {
            throw new BizException("这张单不在你的宿舍");
        }
        return parcel;
    }

    private ParcelView view(long parcelId, Member me) {
        Parcel fresh = parcelRepo.findById(parcelId).orElseThrow(() -> new BizException("没有这张单子"));
        return toViews(me.roomId(), List.of(fresh), me.id()).get(0);
    }


    /** 一次把昵称查出来，避免每条单子都查一次库（N+1 查询）。 */
    private List<ParcelView> toViews(long roomId, List<Parcel> parcels, long viewerId) {
        Map<Long, String> names = memberRepo.listByRoom(roomId).stream()
                .collect(Collectors.toMap(Member::id, Member::nickname, (a, b) -> a, LinkedHashMap::new));
        LocalDateTime now = LocalDateTime.now();
        return parcels.stream()
                .map(p -> {
                    // 隐私：取件码只给发布者和接单人看，其他人拿到 null
                    boolean canSeeCode = p.ownerId() == viewerId
                            || (p.takerId() != null && p.takerId() == viewerId);
                    return new ParcelView(
                            p.id(),
                            p.courier(),
                            p.station(),
                            canSeeCode ? p.pickupCode() : null,
                            p.reward(),
                            p.deadline() == null ? null : p.deadline().format(TEXT_FMT),
                            p.status().name(),
                            p.status().getLabel(),
                            p.version(),
                            p.ownerId(),
                            names.getOrDefault(p.ownerId(), "?"),
                            p.takerId(),
                            p.takerId() == null ? null : names.getOrDefault(p.takerId(), "?"),
                            p.createdAt().format(TEXT_FMT),
                            p.deadline() != null && p.deadline().isBefore(now) && !p.status().isFinished());
                })
                .toList();
    }

    private LocalDateTime parseDeadline(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim().replace(' ', 'T'), INPUT_FMT);
        } catch (Exception e) {
            throw new BizException("期望时间格式不对，应该像 2026-11-09T18:30");
        }
    }

    private String require(String value, String message) {
        String v = emptyToNull(value);
        if (v == null) {
            throw new BizException(message);
        }
        return v;
    }

    private String emptyToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 发布快递单的入参（从 JSON body 转过来）。 */
    public record CreateParcelCommand(
            String courier,
            String station,
            String pickupCode,
            String reward,
            String deadline
    ) {
    }
}
