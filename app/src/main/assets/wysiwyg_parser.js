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
    aaa();
});

document.addEventListener("DOMContentLoaded", (event) => {
    aaa();
});

function eval() {
  return new Promise((resolve) => {
    setTimeout(() => {
        if (document.querySelector("$selector") != null) {
            console.info("resolve OK IMG");
            $interface.$fun(document.URL, document.querySelector("html").innerHTML);
            resolve(true);
        } else if (document.querySelector("#pic_container canvas") != null) {
            console.info("resolve OK CANVAS");
            var element = document.querySelector("#pic_container canvas");
            screencap.onLoaded(element.width, element.height);
            resolve(true);
       } else {
            console.info("resolve KO");
            resolve(false);
        }
    }, 750);
  });
}

async function aaa() {
    console.info("ready");
    var result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
}