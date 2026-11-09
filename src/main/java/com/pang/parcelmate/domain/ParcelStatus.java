package com.pang.parcelmate.domain;

/** 快递单的状态机。一期只有这 6 个状态，所有流转都写在这里，别在别处硬编码字符串。 */
public enum ParcelStatus {

    /** 待接单 */
    OPEN("待接"),
    /** 已被接单，接单人正在取 */
    TAKEN("已接"),
    /** 已从驿站取出 */
    PICKED("已取"),
    /** 已送到发布者手里 */
    DELIVERED("已送达"),
    /** 发布者自己取消了 */
    CANCELED("已取消"),
    /** 异常：丢件、被退回等 */
    EXCEPTION("异常");

    private final String label;

    ParcelStatus(String label) {
        this.label = label;
    }

    /** 给前端展示用的中文名 */
    public String getLabel() {
        return label;
    }

    public boolean isFinished() {
        return this == DELIVERED || this == CANCELED || this == EXCEPTION;
    }
}
