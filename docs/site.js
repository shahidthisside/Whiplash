// Small progressive enhancements; the page works fully without JavaScript.
(function () {
  // Nav border once the page scrolls.
  var nav = document.getElementById("nav");
  if (nav) {
    var onScroll = function () { nav.classList.toggle("scrolled", window.scrollY > 8); };
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  }

  // Fade sections in as they scroll into view.
  var items = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
    items.forEach(function (el) { io.observe(el); });
  } else {
    items.forEach(function (el) { el.classList.add("in"); });
  }

  // Show the latest release's version and APK size.
  fetch("https://api.github.com/repos/shahidthisside/Whiplash/releases/latest", { headers: { Accept: "application/vnd.github+json" } })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (rel) {
      if (!rel || !rel.tag_name) return;
      var tag = rel.tag_name.replace(/^v?/, "v");
      document.querySelectorAll("[data-release]").forEach(function (el) { el.textContent = tag + " is out"; });
      var apk = (rel.assets || []).find(function (a) { return /\.apk$/i.test(a.name); });
      var size = apk ? " · " + (apk.size / 1048576).toFixed(1) + " MB" : "";
      document.querySelectorAll("[data-release-meta]").forEach(function (el) {
        el.textContent = tag + size + " · Android 8.0+ · Open source";
      });
    })
    .catch(function () {});
})();
