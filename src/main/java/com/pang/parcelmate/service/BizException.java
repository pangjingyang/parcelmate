package com.pang.parcelmate.service;

/**
 * 业务异常：表示"用户操作不合法"，比如昵称重复、单子已经被别人接了。
 * Controller 层会把它转成 400 + 一句中文提示，而不是 500 报错。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
