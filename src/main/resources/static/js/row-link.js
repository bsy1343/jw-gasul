// row-link.js — 표의 행(tr) 전체를 눌러 상세로 이동시키는 공통 스크립트.
// tr에 data-row-href를 달면 동작한다. 행 안의 링크·버튼·입력은 각자 동작을 유지한다.
(function () {
    // 클릭 지점이 자체 동작을 가진 요소(링크/버튼/입력/라벨) 안이면 행 이동을 하지 않는다
    function isInteractive(target) {
        return target.closest('a, button, input, select, textarea, label');
    }

    document.addEventListener('click', function (e) {
        var row = e.target.closest('tr[data-row-href]');
        if (!row || isInteractive(e.target)) {
            return;
        }
        // 텍스트를 드래그 선택하던 중이면 이동하지 않는다
        if (window.getSelection && String(window.getSelection()).length > 0) {
            return;
        }
        // 새 탭으로 열기(Ctrl/Cmd/가운데 클릭) 지원
        if (e.metaKey || e.ctrlKey) {
            window.open(row.dataset.rowHref, '_blank', 'noopener');
        } else {
            window.location.href = row.dataset.rowHref;
        }
    });

    // 키보드 접근성: 행에 포커스를 준 상태에서 Enter로 이동
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Enter') {
            return;
        }
        var row = e.target.closest && e.target.closest('tr[data-row-href]');
        if (row && !isInteractive(e.target)) {
            window.location.href = row.dataset.rowHref;
        }
    });
})();
