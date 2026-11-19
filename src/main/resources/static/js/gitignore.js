/* Minimal .gitignore engine: negation, dir-only rules, ** globs and nested .gitignore files. */
(function (global) {
    'use strict';

    function escapeRe(c) {
        return c.replace(/[.*+?^${}()|[\]\\\/]/g, '\\$&');
    }

    function globToRegex(glob) {
        let re = '';
        for (let i = 0; i < glob.length; i++) {
            const c = glob[i];
            if (c === '*') {
                if (glob[i + 1] === '*') {
                    const prevSlash = i === 0 || glob[i - 1] === '/';
                    const nextSlash = glob[i + 2] === '/';
                    if (prevSlash && nextSlash) {            // **/
                        re += '(?:.*/)?';
                        i += 2;
                    } else if (prevSlash && i + 2 >= glob.length) {   // trailing /**
                        re += '.*';
                        i += 1;
                    } else {
                        re += '[^/]*';
                        i += 1;
                    }
                } else {
                    re += '[^/]*';
                }
            } else if (c === '?') {
                re += '[^/]';
            } else if (c === '[') {
                const end = glob.indexOf(']', i + 2);
                if (end > i) {
                    let cls = glob.slice(i + 1, end);
                    if (cls[0] === '!') cls = '^' + cls.slice(1);
                    re += '[' + cls.replace(/\\/g, '\\\\') + ']';
                    i = end;
                } else {
                    re += '\\[';
                }
            } else if (c === '\\' && i + 1 < glob.length) {
                re += escapeRe(glob[i + 1]);
                i++;
            } else {
                re += escapeRe(c);
            }
        }
        return new RegExp('^' + re + '$');
    }

    function stripTrailingUnescapedSpaces(str) {
        let end = str.length;
        while (end > 0 && /\s/.test(str[end - 1])) {
            let backslashes = 0;
            let j = end - 2;
            while (j >= 0 && str[j] === '\\') {
                backslashes++;
                j--;
            }
            if (backslashes % 2 === 1) break; // مسافة مع escaped backslash
            end--;
        }
        return str.slice(0, end);
    }

    function parseRule(line, baseDir) {
        let l = line.replace(/\r$/, '');
        if (!l || l[0] === '#') return null;
        l = stripTrailingUnescapedSpaces(l);
        if (!l) return null;

        let negate = false;
        if (l[0] === '!') {
            negate = true;
            l = l.slice(1);
        } else if (l.startsWith('\\!') || l.startsWith('\\#')) {
            l = l.slice(1);
        }

        let dirOnly = false;
        if (l.endsWith('/')) {
            dirOnly = true;
            l = l.slice(0, -1);
        }
        if (!l) return null;

        const anchored = l.indexOf('/') !== -1;
        if (l[0] === '/') l = l.slice(1);

        return { baseDir: baseDir, negate: negate, dirOnly: dirOnly, anchored: anchored, re: globToRegex(l) };
    }

    /**
     * files: مصفوفة File، pathOf: دالة بترجّع مسار الملف النسبي.
     * بترجّع Promise<{ hasRules, ignores(path) }>
     */
    function build(files, pathOf) {
        const gitignoreFiles = files.filter(function (f) {
            const p = pathOf(f);
            return p === '.gitignore' || p.endsWith('/.gitignore');
        });

        return Promise.all(gitignoreFiles.map(function (f) {
            const p = pathOf(f);
            const baseDir = p === '.gitignore' ? '' : p.slice(0, p.length - '.gitignore'.length);
            return f.text().then(function (text) {
                return {
                    baseDir: baseDir,
                    rules: text.split('\n').map(function (line) { return parseRule(line, baseDir); }).filter(Boolean)
                };
            });
        })).then(function (groups) {
            groups.sort(function (a, b) { return a.baseDir.length - b.baseDir.length; });   // الأعمق آخراً فيكسب
            const rules = [];
            groups.forEach(function (g) { g.rules.forEach(function (r) { rules.push(r); }); });

            const dirCache = new Map();

            function ruleMatches(rule, path, isDir) {
                if (rule.dirOnly && !isDir) return false;
                if (!path.startsWith(rule.baseDir)) return false;
                const rel = path.slice(rule.baseDir.length);
                if (!rel) return false;
                const target = rule.anchored ? rel : rel.slice(rel.lastIndexOf('/') + 1);
                return rule.re.test(target);
            }

            function state(path, isDir) {
                let ignored = false;
                for (let i = 0; i < rules.length; i++) {
                    if (ruleMatches(rules[i], path, isDir)) ignored = !rules[i].negate;
                }
                return ignored;
            }

            function ignores(path) {
                const parts = path.split('/');
                let cur = '';
                for (let i = 0; i < parts.length; i++) {
                    cur = i === 0 ? parts[0] : cur + '/' + parts[i];
                    const isDir = i < parts.length - 1;
                    if (isDir) {
                        let v = dirCache.get(cur);
                        if (v === undefined) {
                            v = state(cur, true);
                            dirCache.set(cur, v);
                        }
                        if (v) return true;          // لو المجلد مستثنى، كل اللي جواه مستثنى
                    } else if (state(cur, false)) {
                        return true;
                    }
                }
                return false;
            }

            return { hasRules: rules.length > 0, ignores: ignores };
        });
    }

    global.GitIgnore = { build: build };
})(window);
