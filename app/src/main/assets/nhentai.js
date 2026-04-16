(() => {
    let oldPushState = history.pushState;
    history.pushState = function pushState() {
        let ret = oldPushState.apply(this, arguments);
        window.dispatchEvent(new Event('pushstate'));
        window.dispatchEvent(new Event('locationchange'));
        return ret;
    };

    let oldReplaceState = history.replaceState;
    history.replaceState = function replaceState() {
        let ret = oldReplaceState.apply(this, arguments);
        window.dispatchEvent(new Event('replacestate'));
        window.dispatchEvent(new Event('locationchange'));
        return ret;
    };

    window.addEventListener('popstate', () => {
        window.dispatchEvent(new Event('locationchange'));
    });
})();

window.addEventListener("locationchange", function () {
    setTimeout(function () {
        markBooks();
    }, 500);
});

document.addEventListener("DOMContentLoaded", (event) => {
    setTimeout(function () {
        markBooks();
    }, 500);
});

function markBooks() {
    console.info("mark books START");
    var targets = document.querySelectorAll(".cover img");
    if (targets != null) {
        for (let t of targets) {
            var link = t.closest("a[href]");
            if (link != null) markBook(link);
        }
    }
}

function markBook(markTarget) {
    var targetBookId = markTarget.getAttribute("href").replace("/g", '');
    var result = nhJsInterface.isMarkable(targetBookId);
    if (result == 1) {
        markTarget.classList.add('watermarked');
    } else if (result == 2) {
        markTarget.classList.add("watermarked-merged");
    } else {
        var classList = markTarget.classList;
        if (classList.contains("watermarked")) {
            markTarget.classList.remove("watermarked");
        } else if (classList.contains("watermarked-merged")) {
            markTarget.classList.remove("watermarked-merged");
        }
    }
}