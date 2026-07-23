// worker-account.js — 계좌 전체보기/복사(F-09). 서버 reveal 엔드포인트 호출 = 감사 로그(VIEW) 기록됨.
(function () {
    'use strict';

    // 서버에서 계좌번호 전체(하이픈 제외 숫자)를 가져온다. 호출 자체가 감사 기록을 남긴다.
    async function reveal(card) {
        var wid = card.getAttribute('data-worker-id');
        var aid = card.getAttribute('data-account-id');
        var res = await fetch('/workers/' + wid + '/accounts/' + aid + '/reveal');
        if (!res.ok) {
            throw new Error('조회 실패');
        }
        return (await res.text()).trim();
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-account-id]').forEach(function (card) {
            var numEl = card.querySelector('.acct-number');
            var revealBtn = card.querySelector('.acct-reveal');
            var copyBtn = card.querySelector('.acct-copy');

            if (revealBtn && numEl) {
                revealBtn.addEventListener('click', async function () {
                    try {
                        numEl.textContent = await reveal(card);
                        revealBtn.textContent = '노출됨';
                        revealBtn.disabled = true;
                    } catch (e) {
                        alert('계좌번호를 불러오지 못했습니다.');
                    }
                });
            }

            if (copyBtn) {
                copyBtn.addEventListener('click', async function () {
                    try {
                        var full = await reveal(card);
                        await navigator.clipboard.writeText(full);
                        copyBtn.textContent = '복사됨';
                        setTimeout(function () { copyBtn.textContent = '복사'; }, 1500);
                    } catch (e) {
                        alert('복사하지 못했습니다.');
                    }
                });
            }
        });
    });
})();
