// DuplicateWorkerException.java — 동일인(이름·생년월일·전화) 중복 등록 시도 예외(3.1)
package com.jwgasul.common.exception;

public class DuplicateWorkerException extends RuntimeException {

    public DuplicateWorkerException(String message) {
        super(message);
    }
}
