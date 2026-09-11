// Push the current reader theme's colors into the page as CSS custom properties, so user-provided
// CSS/JS (dictionary popups, custom styling, etc.) can reference them with var(...) and always
// match whatever the person is currently using. Two independent groups are expected in the payload:
//   --reader-*  - the novel reader's own reading colors (background/text), user-customizable
//                 per readerPreferences.novelBackgroundColor / novelFontColor / novelTheme
//   --app-*     - the app's overall Material color scheme (primary/surface/etc.), including
//                 cover-based "theme from cover" colors when that setting is on
// Callers decide the exact variable names via the payload; this script doesn't assume either group.
//
// Replaces:
//   __COLOR_VARS_JSON__ - JSON object of { "--custom-property-name": "#rrggbb", ... } pairs
//
// Writes each var only when its value actually changed: setting a custom property invalidates
// style for its whole subtree, an avoidable recalc on a long document on every recomposition.
(function () {
    var colors = __COLOR_VARS_JSON__;
    var s = document.documentElement.style;
    for (var name in colors) {
        if (!Object.prototype.hasOwnProperty.call(colors, name)) continue;
        var value = colors[name];
        if (s.getPropertyValue(name) !== value) {
            s.setProperty(name, value);
        }
    }
})();
