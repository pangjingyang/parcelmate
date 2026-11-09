package com.pang.parcelmate.service;

import com.pang.parcelmate.domain.Member;
import com.pang.parcelmate.domain.Notice;
import com.pang.parcelmate.domain.Parcel;
import com.pang.parcelmate.repo.NoticeRepository;
import com.pang.parcelmate.repo.ParcelRepository;
import com.pang.parcelmate.web.dto.NoticeView;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 站内提醒：把 notice 表里那行"机器可读的 type"翻译成人话。
 *
 * <p>为什么不在写入的时候就存好文案？
 * 因为文案会改（比如以后要加"再接一单吧"），存文案等于把展示层钉死在数据库里；
 * 存 type 则只是记"发生了什么"，怎么说是前端/服务层的事。这也是 MQ、事件表常见的设计。
 */
@Service
public class NoticeService {

    private static final DateTimeFormatter TEXT_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final NoticeRepository noticeRepo;
    private final ParcelRepository parcelRepo;

    public NoticeService(NoticeRepository noticeRepo, ParcelRepository parcelRepo) {
        this.noticeRepo = noticeRepo;
        this.parcelRepo = parcelRepo;
    }

    public List<NoticeView> listUnread(Member me) {
        return noticeRepo.listUnread(me.id()).stream().map(this::toView).toList();
    }

    public int countUnread(Member me) {
        return noticeRepo.countUnread(me.id());
    }

    public void markAllRead(Member me) {
        noticeRepo.markAllRead(me.id());
    }

    private NoticeView toView(Notice n) {
        String courier = n.parcelId() == null ? "快递"
                : parcelRepo.findById(n.parcelId()).map(Parcel::courier).orElse("快递");
        return new NoticeView(n.id(), n.parcelId(), text(n.type(), courier), n.at().format(TEXT_FMT));
    }

    private String text(String type, String courier) {
        return switch (type) {
            case "TAKEN" -> "你的" + courier + "快递被接了，等对方送到";
            case "PICKED" -> "接单人已经把" + courier + "快递从驿站取出来了";
            case "DELIVERED" -> "你的" + courier + "快递已确认送达，记得说声谢谢";
            case "CANCELED" -> "你的" + courier + "快递被取消了";
            default -> "你的" + courier + "快递有新动态";
        };
    }
}
