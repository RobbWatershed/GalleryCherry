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
    scheduleAll();
});

document.addEventListener("DOMContentLoaded", (event) => {
    scheduleAll();
});

function scheduleAll() {
    scheduleMark(500);
    scheduleMark(1000);
    scheduleMark(1500);
    scheduleMark(2000);
    scheduleMark(2500);
    scheduleMark(3000);
    scheduleMark(3500);
    scheduleMark(4000);
}

function scheduleMark(delay) {
    setTimeout(function () {
        markBooks();
    }, delay);
}

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
    } else if (result == 3) {
        markTarget.classList.add("watermarked-queued");
    } else {
        var classList = markTarget.classList;
        if (classList.contains("watermarked")) {
            markTarget.classList.remove("watermarked");
        } else if (classList.contains("watermarked-merged")) {
            markTarget.classList.remove("watermarked-merged");
        } else if (classList.contains("watermarked-queued")) {
            markTarget.classList.remove("watermarked-queued");
        }
    }
}


window.origAppendChild = Element.prototype.appendChild;
Element.prototype.appendChild = function() {
    if (typeof (arguments[0].src) != 'undefined' && arguments[0].src.includes("cdn.tsyndicate.com")) {
        console.info(arguments[0].src + " : nope!");
    } else {
        return window.origAppendChild.apply(this, arguments);
    }
};