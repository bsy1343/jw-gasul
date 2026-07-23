// DocType.java — 서류 사진 슬롯 종류(신분증 앞/뒤/이수증). worker_document.doc_type과 매핑(3.2)
package com.jwgasul.worker;

public enum DocType {
    ID_FRONT("신분증 앞"),
    ID_BACK("신분증 뒤"),
    EDU_CERT("교육 이수증");

    private final String label;

    DocType(String label) {
        this.label = label;
    }

    // 화면 표시용 한글 라벨
    public String getLabel() {
        return label;
    }
}
