package com.pang.parcelmate.service;

import com.pang.parcelmate.domain.Member;
import com.pang.parcelmate.domain.Room;
import com.pang.parcelmate.repo.MemberRepository;
import com.pang.parcelmate.repo.NoticeRepository;
import com.pang.parcelmate.repo.RoomRepository;
import com.pang.parcelmate.web.dto.MeView;
import com.pang.parcelmate.web.dto.MemberView;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 宿舍与成员的业务逻辑（M1）。
 *
 * <p>这一层负责"什么能做什么不能做"：昵称不能重复、房间码必须存在……
 * Repository 只认 SQL，Controller 只认 HTTP，规则集中放这里。
 */
@Service
public class RoomService {

    /** 房间码用的字符：去掉了容易看混的 0/O/1/I。 */
    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    /** 5 分钟内活跃过，就算"在线"。 */
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);

    private static final DateTimeFormatter TEXT_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final SecureRandom random = new SecureRandom();
    private final RoomRepository roomRepo;
    private final MemberRepository memberRepo;
    private final NoticeRepository noticeRepo;

    public RoomService(RoomRepository roomRepo, MemberRepository memberRepo, NoticeRepository noticeRepo) {
        this.roomRepo = roomRepo;
        this.memberRepo = memberRepo;
        this.noticeRepo = noticeRepo;
    }

    /** 建房：返回房间码，室友拿这个码加入。 */
    public Room createRoom(String rawName) {
        String name = trimToNull(rawName);
        if (name == null) {
            throw new BizException("宿舍名字不能为空");
        }
        if (name.length() > 20) {
            throw new BizException("宿舍名字最多 20 个字");
        }
        String code = generateUniqueCode();
        long id = roomRepo.insert(code, name);
        return roomRepo.findById(id).orElseThrow();
    }

    /** 加入：房间码 + 昵称，昵称在宿舍内唯一。 */
    public Member join(String rawCode, String rawNickname) {
        String code = trimToNull(rawCode);
        String nickname = trimToNull(rawNickname);
        if (code == null) {
            throw new BizException("请输入房间码");
        }
        if (nickname == null) {
            throw new BizException("请输入昵称");
        }
        if (nickname.length() > 10) {
            throw new BizException("昵称最多 10 个字");
        }
        Room room = roomRepo.findByCode(code.toUpperCase())
                .orElseThrow(() -> new BizException("房间码不存在，问问室友是不是打错了"));

        // 同名 = 找回原身份。换手机、清了浏览器 cookie、点过"退出"再回来，
        // 都靠这一步。不这么做的话，室友每换一次设备就变成一个新成员，
        // "我发布的"就再也看不到自己以前的单子了。
        for (Member m : memberRepo.listByRoom(room.id())) {
            if (m.nickname().equals(nickname)) {
                return m;
            }
        }

        long memberId = memberRepo.insert(room.id(), nickname);
        return memberRepo.findById(memberId).orElseThrow();
    }

    /** 页面初始化：我是谁、我在哪个宿舍、宿舍里都有谁、我有几条未读。 */
    public MeView me(long memberId) {
        Member me = memberRepo.findById(memberId)
                .orElseThrow(() -> new BizException("请先加入宿舍"));
        Room room = roomRepo.findById(me.roomId())
                .orElseThrow(() -> new BizException("宿舍不存在了"));
        memberRepo.touch(memberId);

        LocalDateTime now = LocalDateTime.now();
        List<MemberView> members = memberRepo.listByRoom(room.id()).stream()
                .map(m -> new MemberView(
                        m.id(),
                        m.nickname(),
                        Duration.between(m.lastActive(), now).compareTo(ONLINE_WINDOW) < 0,
                        m.lastActive().format(TEXT_FMT)))
                .toList();

        return new MeView(true, me.id(), me.nickname(), room, members, noticeRepo.countUnread(memberId));
    }

    /** 校验 cookie 里的 memberId 是否还有效；无效返回 null。 */
    public Member findMemberOrNull(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return memberRepo.findById(memberId).orElse(null);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            if (!roomRepo.existsCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("生成房间码失败，请重试");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
