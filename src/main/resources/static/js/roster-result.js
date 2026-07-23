// roster-result.js — 랜덤 명부 결과: 개별 제외(행 삭제) / 교체(서버에서 잔여 후보 1명 받아 교체)(F-06)
(function () {
    'use strict';

    var form = document.getElementById('roster-result-form');
    var rows = document.getElementById('roster-rows');
    var countEl = document.getElementById('member-count');
    if (!form || !rows) {
        return;
    }

    function updateCount() {
        countEl.textContent = rows.querySelectorAll('.roster-row').length;
    }

    function currentIds() {
        return Array.prototype.map.call(rows.querySelectorAll('.roster-row'),
            function (r) { return r.getAttribute('data-id'); });
    }

    // 조건(hidden) + 현재 선택 + CSRF를 폼 인코딩으로 만든다
    function replaceParams() {
        var p = new URLSearchParams();
        ['siteId', 'title', 'targetDate', 'count', 'type',
            'excludeVisaExpired', 'excludeVisaImminent', 'excludeEduExpired', 'excludeMissingDoc']
            .forEach(function (name) {
                var el = form.querySelector('input[name="' + name + '"]');
                if (el && el.value) {
                    p.append(name, el.value);
                }
            });
        currentIds().forEach(function (id) { p.append('currentIds', id); });
        var csrf = form.querySelector('input[name="_csrf"]');
        if (csrf) {
            p.append('_csrf', csrf.value);
        }
        return p;
    }

    async function replaceRow(row) {
        var res = await fetch('/roster/random/replace', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: replaceParams().toString()
        });
        if (!res.ok) {
            throw new Error('fail');
        }
        var data = await res.json();
        if (!data.ok) {
            alert('교체할 후보가 없습니다.');
            return;
        }
        row.setAttribute('data-id', data.id);
        row.querySelector('input[name="workerIds"]').value = data.id;
        row.querySelector('.row-name').textContent = data.nameKo;
        // 텍스트만 조합 → XSS 방지 위해 textContent 사용(외국 이름은 사용자 입력)
        row.querySelector('.row-sub').textContent =
            (data.nameForeign ? data.nameForeign + ' · ' : '') + data.phone + ' · ' + data.birthDate;
    }

    rows.addEventListener('click', function (e) {
        var row = e.target.closest('.roster-row');
        if (!row) {
            return;
        }
        if (e.target.classList.contains('row-remove')) {
            row.remove();
            updateCount();
        } else if (e.target.classList.contains('row-replace')) {
            replaceRow(row).catch(function () { alert('교체하지 못했습니다.'); });
        }
    });
})();
