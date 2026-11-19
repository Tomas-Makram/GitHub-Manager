/* Client-side secret scanner: runs in the browser, file contents never leave the machine. */
(function (global) {
    'use strict';

    const MAX_CONTENT_BYTES = 2 * 1024 * 1024;
    const MAX_LINE = 3000;
    const MAX_PER_FILE = 5;

    const SKIP_EXT = /\.(png|jpe?g|gif|webp|ico|bmp|svgz?|pdf|zip|gz|tgz|rar|7z|jar|war|class|exe|dll|so|dylib|bin|woff2?|ttf|otf|eot|mp3|mp4|mov|avi|wav|ogg|webm|psd|ai|lock|map)$/i;
    const SKIP_NAMES = /(^|\/)(package-lock\.json|yarn\.lock|pnpm-lock\.yaml|composer\.lock|[^\/]*\.min\.(js|css))$/i;
    const CONFIG_FILE = /(\.(properties|env|ini|conf|cfg|toml|ya?ml|txt|sh|bat|cmd|ps1)$)|((^|\/)\.env)/i;

    // ملفات حساسة بمجرد اسمها
    const SENSITIVE_FILES = [
        { re: /(^|\/)\.env(\.[^\/]+)?$/i, ok: /\.(example|sample|template|dist)$/i, label: 'Environment file (.env)' },
        { re: /\.(pem|key|p12|pfx|jks|keystore|ppk|kdbx)$/i, label: 'Key / certificate file' },
        { re: /(^|\/)id_(rsa|dsa|ecdsa|ed25519)$/i, label: 'SSH private key' },
        { re: /(^|\/)(\.npmrc|\.pypirc|\.netrc|\.htpasswd|\.git-credentials)$/i, label: 'Credentials file' },
        { re: /(^|\/)(credentials|secrets?|service[-_]?account[^\/]*)\.(json|ya?ml|xml|properties)$/i, label: 'Credentials file' }
    ];

    // مفاتيح لها شكل معروف
    const TOKEN_RULES = [
        { type: 'Private key', re: /-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP |ENCRYPTED )?PRIVATE KEY(?: BLOCK)?-----/, hidden: true },
        { type: 'AWS access key ID', re: /\b(?:AKIA|ASIA)[0-9A-Z]{16}\b/ },
        { type: 'AWS secret access key', re: /(?:aws_secret_access_key|aws_secret_key)\s*[:=]\s*["']?([A-Za-z0-9/+=]{40})["']?/i, group: 1 },
        { type: 'GitHub token', re: /\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{50,})\b/ },
        { type: 'Google API key', re: /\bAIza[0-9A-Za-z_-]{35}\b/ },
        { type: 'Slack token', re: /\bxox[abprs]-[0-9A-Za-z-]{10,}\b/ },
        { type: 'Stripe live key', re: /\b[sr]k_live_[0-9A-Za-z]{20,}\b/ },
        { type: 'JWT', re: /\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/ },
        { type: 'Credentials in URL', re: /\b[a-z][a-z0-9+.-]*:\/\/[^\s:\/@'"]+:([^\s:\/@'"]{3,})@[^\s\/'"]+/i, group: 1, checkPlaceholder: true }
    ];

    // قيم مفاتيح مكتوبة في الكود أو الإعدادات
    const KEYWORD = '(?:password|passwd|pwd|secret|api[_.-]?key|apikey|access[_.-]?key|auth[_.-]?token|access[_.-]?token|token|private[_.-]?key|client[_.-]?secret)';
    const NAME = '[A-Za-z0-9_.-]*' + KEYWORD + '[A-Za-z0-9_.-]*';
    const QUOTED = new RegExp('(' + NAME + ')["\']?\\s*[:=]\\s*(["\'`])([^"\'`\\s]{8,})\\2', 'i');
    const UNQUOTED = new RegExp('^\\s*(' + NAME + ')\\s*[:=]\\s*([^\\s#"\'`]{8,})\\s*(?:#.*)?$', 'i');

    const NOT_A_SECRET_NAME = /(uri|url|endpoint|path|file|type|name|header|expir\w*|ttl|length|size|prefix|method|attribute|enabled|policy|mode|format|class)$/i;
    const PLACEHOLDER = /^(?:\$\{.*\}|\$[A-Za-z_][A-Za-z0-9_]*|<.*>|\{\{.*\}\}|%.*%|\[.*\]|\*+|x+|-+|\.+|null|none|nil|undefined|true|false|changeme|change[-_]?me|your[-_ ]?.*|example.*|sample.*|dummy.*|placeholder.*|test.*|password|secret|token|redacted.*|process\.env.*|os\.environ.*|getenv.*|env\(.*)$/i;

    function isPlaceholder(value) {
        return PLACEHOLDER.test(value) || /^https?:\/\//i.test(value);
    }

    function mask(value) {
        if (!value) return '';
        const head = value.slice(0, 2);
        const tail = value.length > 10 ? value.slice(-2) : '';
        return head + '\u2022\u2022\u2022\u2022\u2022' + tail;
    }

    function scanLine(rawLine, isConfig) {
        const line = rawLine.length > MAX_LINE ? rawLine.slice(0, MAX_LINE) : rawLine;

        for (let i = 0; i < TOKEN_RULES.length; i++) {
            const rule = TOKEN_RULES[i];
            const m = rule.re.exec(line);
            if (!m) continue;
            const value = rule.group ? m[rule.group] : m[0];
            if (rule.checkPlaceholder && isPlaceholder(value)) continue;
            return { type: rule.type, preview: rule.hidden ? '' : mask(value) };
        }

        let m = QUOTED.exec(line);
        if (m && !NOT_A_SECRET_NAME.test(m[1]) && !isPlaceholder(m[3])) {
            return { type: 'Hardcoded secret', preview: m[1] + ' = ' + mask(m[3]) };
        }

        if (isConfig) {
            m = UNQUOTED.exec(line);
            if (m && !NOT_A_SECRET_NAME.test(m[1]) && !isPlaceholder(m[2])) {
                return { type: 'Hardcoded secret', preview: m[1] + ' = ' + mask(m[2]) };
            }
        }
        return null;
    }

    /** بترجّع Promise<[{ path, line, type, preview }]> (line = 0 لو الملف نفسه حساس) */
    async function scanFile(file, path) {
        const findings = [];

        for (let i = 0; i < SENSITIVE_FILES.length; i++) {
            const rule = SENSITIVE_FILES[i];
            if (rule.re.test(path) && !(rule.ok && rule.ok.test(path))) {
                findings.push({ path: path, line: 0, type: rule.label, preview: '' });
                break;
            }
        }

        if (SKIP_EXT.test(path) || SKIP_NAMES.test(path) || file.size === 0 || file.size > MAX_CONTENT_BYTES) {
            return findings;
        }

        let text;
        try {
            text = await file.text();
        } catch (e) {
            return findings;
        }
        if (text.indexOf('\u0000') !== -1) return findings;      // ملف ثنائي

        const isConfig = CONFIG_FILE.test(path);
        const lines = text.split(/\r?\n/);

        for (let i = 0; i < lines.length && findings.length < MAX_PER_FILE; i++) {
            const hit = scanLine(lines[i], isConfig);
            if (hit) findings.push({ path: path, line: i + 1, type: hit.type, preview: hit.preview });
        }
        return findings;
    }

    global.SecretScanner = { scanFile: scanFile };
})(window);
