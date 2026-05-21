function xmlHighlight() {
    function escapeHtml(source) {
        return source.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function highlightXml(text) {
        var result = '';
        var cursorIndex = 0;

        while (cursorIndex < text.length) {
            if (text.startsWith('<!--', cursorIndex)) {
                var commentEnd = text.indexOf('-->', cursorIndex + 4);
                commentEnd = commentEnd === -1 ? text.length : commentEnd + 3;
                result += '<span class="xml-comment">' + escapeHtml(text.slice(cursorIndex, commentEnd)) + '</span>';
                cursorIndex = commentEnd;
                continue;
            }

            if (text.startsWith('<![CDATA[', cursorIndex)) {
                var cdataEnd = text.indexOf(']]>', cursorIndex + 9);
                cdataEnd = cdataEnd === -1 ? text.length : cdataEnd + 3;
                result += '<span class="xml-cdata">' + escapeHtml(text.slice(cursorIndex, cdataEnd)) + '</span>';
                cursorIndex = cdataEnd;
                continue;
            }

            if (text.startsWith('<!', cursorIndex)) {
                var declarationEnd = text.indexOf('>', cursorIndex + 2);
                declarationEnd = declarationEnd === -1 ? text.length : declarationEnd + 1;
                result += '<span class="xml-doctype">' + escapeHtml(text.slice(cursorIndex, declarationEnd)) + '</span>';
                cursorIndex = declarationEnd;
                continue;
            }

            if (text.startsWith('<?', cursorIndex)) {
                var prologEnd = text.indexOf('?>', cursorIndex + 2);
                prologEnd = prologEnd === -1 ? text.length : prologEnd + 2;
                result += '<span class="xml-prolog">' + escapeHtml(text.slice(cursorIndex, prologEnd)) + '</span>';
                cursorIndex = prologEnd;
                continue;
            }

            if (text.charAt(cursorIndex) === '<') {
                var tagEnd = cursorIndex + 1;
                var insideQuote = null;
                while (tagEnd < text.length) {
                    var character = text.charAt(tagEnd);
                    tagEnd++;
                    if (insideQuote !== null) {
                        if (character === insideQuote) insideQuote = null;
                    } else if (character === '"' || character === "'") {
                        insideQuote = character;
                    } else if (character === '>') {
                        break;
                    }
                }

                var tagPieces = text.slice(cursorIndex, tagEnd)
                    .match(/^<(\/?)(\s*)([^\s\/]+)([\s\S]*?)(\/?)>$/);
                if (!tagPieces) {
                    result += '<span class="xml-bracket">' + escapeHtml(text.slice(cursorIndex, tagEnd)) + '</span>';
                } else {
                    result += '<span class="xml-bracket">&lt;' + tagPieces[1] + '</span>'
                            + tagPieces[2]
                            + '<span class="xml-tag">' + escapeHtml(tagPieces[3]) + '</span>'
                            + tagPieces[4].replace(
                                /(\s+)([\w:.\-]+)(\s*=\s*)("[^"]*"|'[^']*')/g,
                                function (_, whitespace, attributeName, equals, attributeValue) {
                                    return whitespace
                                         + '<span class="xml-attr">' + escapeHtml(attributeName) + '</span>'
                                         + equals
                                         + '<span class="xml-value">' + escapeHtml(attributeValue) + '</span>';
                                })
                            + '<span class="xml-bracket">' + tagPieces[5] + '&gt;</span>';
                }
                cursorIndex = tagEnd;
                continue;
            }

            var nextTagStart = text.indexOf('<', cursorIndex);
            if (nextTagStart === -1) nextTagStart = text.length;
            result += escapeHtml(text.slice(cursorIndex, nextTagStart));
            cursorIndex = nextTagStart;
        }

        return result;
    }

    function isReadOnly(element, controller) {
        if (element && element.closest && element.closest('.is-readonly')) return true;
        return controller && typeof controller.isReadOnly === 'function' && controller.isReadOnly();
    }

    return {
        render: function (element) {
            var pre = document.createElement('pre');
            pre.className = 'xml-highlight-pre';

            var code = document.createElement('code');
            code.className = 'xml-highlight-code';
            pre.appendChild(code);

            element.appendChild(pre);
            element._xmlHighlight = { pre: pre, code: code, textarea: null, lastValue: '' };

            pre.addEventListener('click', function () {
                var state = element._xmlHighlight;
                if (isReadOnly(element, state.controller)) return;

                var textarea = document.createElement('textarea');
                textarea.className = 'xml-highlight-textarea';
                textarea.spellcheck = false;
                textarea.value = state.lastValue;

                textarea.addEventListener('input', function () {
                    if (!isReadOnly(element, state.controller) && state.controller) {
                        state.controller.changeValue(textarea.value);
                    }
                });
                textarea.addEventListener('blur', function () {
                    state.lastValue = textarea.value;
                    state.code.innerHTML = highlightXml(textarea.value);
                    element.removeChild(textarea);
                    element.appendChild(state.pre);
                    state.textarea = null;
                });

                function stop(event) { event.stopPropagation(); }
                textarea.addEventListener('keydown', function (event) {
                    if (event.key === 'Escape') {
                        textarea.blur();
                        return;
                    }
                    event.stopPropagation();
                });

                textarea.addEventListener('copy', stop);
                textarea.addEventListener('cut', stop);
                textarea.addEventListener('paste', stop);
                textarea.addEventListener('contextmenu', stop);

                state.textarea = textarea;
                element.removeChild(state.pre);
                element.appendChild(textarea);
                textarea.focus();
            });
        },
        update: function (element, controller, value) {
            var state = element._xmlHighlight;
            if (!state) return;
            state.controller = controller;
            var text = value == null ? '' : String(value);
            state.lastValue = text;
            if (state.textarea && document.activeElement !== state.textarea) {
                state.textarea.value = text;
            }
            state.code.innerHTML = highlightXml(text);
        }
    };
}
