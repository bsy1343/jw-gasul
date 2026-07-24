// worker-form.js — 근로자 폼: 유형(외국인/한국인) 선택에 따라 외국인 전용 필드를 표시/숨김(F-02)
(function () {
    'use strict';

    // 선택된 유형에 맞춰 [data-foreign-only] 요소를 토글한다
    function applyType() {
        var selected = document.querySelector('input[name="workerType"]:checked');
        var isForeign = selected ? selected.value === 'FOREIGN' : true;
        document.querySelectorAll('[data-foreign-only]').forEach(function (el) {
            el.style.display = isForeign ? '' : 'none';
        });
    }

    // 날짜 텍스트 입력 자동 하이픈: 숫자만 입력해도 YYYY-MM-DD로 정리(달력 팝업 없이 직접 편집)
    function bindDateAutoFormat(input) {
        input.addEventListener('input', function () {
            var digits = input.value.replace(/\D/g, '').slice(0, 8);
            var out = digits;
            if (digits.length > 6) {
                out = digits.slice(0, 4) + '-' + digits.slice(4, 6) + '-' + digits.slice(6);
            } else if (digits.length > 4) {
                out = digits.slice(0, 4) + '-' + digits.slice(4);
            }
            input.value = out;
        });
    }

    // 숫자만 남긴 연락처를 010-1234-5678 형태로 조립(입력 중간 단계도 자연스럽게)
    function formatPhone(value) {
        var d = value.replace(/\D/g, '').slice(0, 11);
        if (d.indexOf('02') === 0 && d.length <= 10) {       // 서울 지역번호(02-1234-5678)
            if (d.length > 6) { return d.slice(0, 2) + '-' + d.slice(2, 6) + '-' + d.slice(6); }
            if (d.length > 2) { return d.slice(0, 2) + '-' + d.slice(2); }
            return d;
        }
        if (d.length > 7) { return d.slice(0, 3) + '-' + d.slice(3, 7) + '-' + d.slice(7); }
        if (d.length > 3) { return d.slice(0, 3) + '-' + d.slice(3); }
        return d;
    }

    // 연락처 자동 하이픈. 저장 시 서버가 숫자만 남기므로(normalizePhone) 하이픈은 표시용이다.
    function bindPhoneAutoFormat(input) {
        input.value = formatPhone(input.value);   // 기존 저장값(숫자만)도 열자마자 포맷
        input.addEventListener('input', function () {
            input.value = formatPhone(input.value);
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('input[data-date-input]').forEach(bindDateAutoFormat);
        document.querySelectorAll('input[data-phone-input]').forEach(bindPhoneAutoFormat);

        var radios = document.querySelectorAll('input[name="workerType"]');
        if (radios.length === 0) {
            return;
        }
        // 신규 등록 시 아무것도 선택 안 됐으면 외국인을 기본 선택
        var anyChecked = Array.prototype.some.call(radios, function (r) { return r.checked; });
        if (!anyChecked) {
            var foreign = document.querySelector('input[name="workerType"][value="FOREIGN"]');
            if (foreign) { foreign.checked = true; }
        }
        radios.forEach(function (r) { r.addEventListener('change', applyType); });
        applyType();
    });
})();
