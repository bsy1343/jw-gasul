// DuplicateSiteException.java — 현장명 중복 등록 시도 예외(3.5)
package com.jwgasul.common.exception;

public class DuplicateSiteException extends RuntimeException {

    public DuplicateSiteException(String message) {
        super(message);
    }
}
