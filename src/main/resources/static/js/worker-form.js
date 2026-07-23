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

    document.addEventListener('DOMContentLoaded', function () {
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
