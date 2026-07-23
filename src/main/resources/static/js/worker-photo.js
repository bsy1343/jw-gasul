// worker-photo.js — 서류 사진 업로드 전처리(F-02): 장변 1600px 리사이즈 + EXIF 회전 보정 + JPEG 변환.
// 서버는 jpg/png만 받으므로 클라이언트에서 변환한다. HEIC는 브라우저 디코딩 가능 시 처리(불가 시 안내).
(function () {
    'use strict';

    var MAX_EDGE = 1600;
    var JPEG_QUALITY = 0.85;

    // 선택된 이미지를 리사이즈·JPEG 변환한 File로 만든다
    async function toResizedJpeg(file) {
        // imageOrientation: 'from-image' 로 EXIF 회전 보정을 브라우저에 위임
        var bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
        var scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
        var w = Math.round(bitmap.width * scale);
        var h = Math.round(bitmap.height * scale);

        var canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        canvas.getContext('2d').drawImage(bitmap, 0, 0, w, h);

        var blob = await new Promise(function (resolve) {
            canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY);
        });
        var baseName = (file.name || 'photo').replace(/\.[^.]+$/, '') || 'photo';
        return new File([blob], baseName + '.jpg', { type: 'image/jpeg' });
    }

    // 파일 선택 시 전처리 후 폼을 자동 제출한다
    function bind(form) {
        var input = form.querySelector('input[type="file"]');
        if (!input) {
            return;
        }
        input.addEventListener('change', async function () {
            var file = input.files && input.files[0];
            if (!file) {
                return;
            }
            input.disabled = true;
            try {
                var jpeg = await toResizedJpeg(file);
                var dt = new DataTransfer();
                dt.items.add(jpeg);
                input.files = dt.files;
                input.disabled = false;
                form.submit();
            } catch (e) {
                input.disabled = false;
                input.value = '';
                alert('이미지를 처리할 수 없습니다. jpg 또는 png로 변환 후 업로드하세요.\n(' + (e && e.message ? e.message : e) + ')');
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.photo-upload-form').forEach(bind);
    });
})();
