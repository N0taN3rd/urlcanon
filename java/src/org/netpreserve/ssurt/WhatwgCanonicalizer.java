/*
 * WhatwgCanonicalizer.java - WHATWG-compatible url canonicalizer
 * Java port of canon.py
 *
 * Copyright (C) 2016 Internet Archive
 * Copyright (C) 2016 National Library of Australia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.netpreserve.ssurt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

class WhatwgCanonicalizer implements Canonicalizer {
    private static final Pattern PATH_SEGMENT_REGEX = Pattern.compile("(?:([.]|%2e)([.]|%2e)?|[^/\\\\]*)(?:[/\\\\]|\\Z)", CASE_INSENSITIVE);
    private static final Pattern PCT2D_REGEX = Pattern.compile("%2e", CASE_INSENSITIVE);
    /*
     * > The simple encode set are C0 controls and all code points greater than
     * > U+007E."
     * > The default encode set is the simple encode set and code points U+0020,
     * > '"', "#", "<", ">", "?", "`", "{", and "}".
     */
    private static final Pattern DEFAULT_ENCODE_REGEX = Pattern.compile("[\\x00-\\x20\\x7f-\\xff\"#<>?`{}]");
    private static final Pattern TAB_AND_NEWLINE_REGEX = Pattern.compile("[\\x09\\x0a\\x0d]");

    void removeLeadingTrailingJunk(ParsedUrl url) {
        url.leadingJunk = "";
        url.trailingJunk = "";
    }

    static String removeTabsAndNewlines(String s) {
        return TAB_AND_NEWLINE_REGEX.matcher(s).replaceAll("");
    }

    void removeTabsAndNewlines(ParsedUrl url) {
        url.leadingJunk = removeTabsAndNewlines(url.leadingJunk);
        url.scheme = removeTabsAndNewlines(url.scheme);
        url.colonAfterScheme = removeTabsAndNewlines(url.colonAfterScheme);
        url.slashes = removeTabsAndNewlines(url.slashes);
        url.username = removeTabsAndNewlines(url.username);
        url.colonBeforePassword = removeTabsAndNewlines(url.colonBeforePassword);
        url.password = removeTabsAndNewlines(url.password);
        url.atSign = removeTabsAndNewlines(url.atSign);
        url.ip6 = removeTabsAndNewlines(url.ip6);
        url.ip4 = removeTabsAndNewlines(url.ip4);
        url.domain = removeTabsAndNewlines(url.domain);
        url.colonBeforePort = removeTabsAndNewlines(url.colonBeforePort);
        url.port = removeTabsAndNewlines(url.port);
        url.path = removeTabsAndNewlines(url.path);
        url.questionMark = removeTabsAndNewlines(url.questionMark);
        url.query = removeTabsAndNewlines(url.query);
        url.hashSign = removeTabsAndNewlines(url.hashSign);
        url.fragment = removeTabsAndNewlines(url.fragment);
        url.trailingJunk = removeTabsAndNewlines(url.trailingJunk);
    }

    void lowercaseScheme(ParsedUrl url) {
        url.scheme = url.scheme.toLowerCase();
    }

    void fixBackslashes(ParsedUrl url) {
        url.slashes = url.slashes.replace('\\', '/');
        String path = url.path;
        if (!path.isEmpty()) {
            char c = path.charAt(0);
            if (c == '/' || c == '\\') {
                url.path = path.replace('\\', '/');
            }
        }
    }

    String resolvePathDots(String path) {
        if (!path.isEmpty() && (path.charAt(0) == '/' || path.charAt(0) == '\\')) {
            StringBuilder buf = new StringBuilder("/");
            Deque<Integer> segmentOffsets = new ArrayDeque<>();
            Matcher m = PATH_SEGMENT_REGEX.matcher(path);
            m.region(1, path.length());
            while (m.lookingAt()) {
                if (m.start(2) != -1) {
                    // "../" => pop last segment
                    buf.setLength(segmentOffsets.isEmpty() ? 1 : segmentOffsets.pop());
                } else if (m.start(1) != -1) {
                    // "./" => do nothing
                } else {
                    // push new segment
                    segmentOffsets.push(buf.length());
                    buf.append(path, m.start(), m.end());
                }
                m.region(m.end(), path.length());
            }
            return buf.toString();
        } else {
            return path;
        }
    }

    void normalizePathDots(ParsedUrl url) {
        url.path = resolvePathDots(url.path);
    }

    void decodePath2e(ParsedUrl url) {
        url.path = PCT2D_REGEX.matcher(url.path).replaceAll(".");
    }

    void pctEncodePath(ParsedUrl url) {
        StringBuilder buf = new StringBuilder();
        Matcher m = DEFAULT_ENCODE_REGEX.matcher(url.path);
        int pos = 0;
        while (m.find()) {
            buf.append(url.path, pos, m.start());
            buf.append('%');
            int b = (url.path.charAt(m.start())) & 0xff;
            buf.append(Character.forDigit(b >> 4, 16));
            buf.append(Character.forDigit(b & 0xf, 16));
            pos = m.end();
        }
        buf.append(url.path, pos, url.path.length());
        url.path = buf.toString();
    }

    void emptyPathToSlash(ParsedUrl url) {
        if (url.path.isEmpty() && url.hasAuthority()) {
            url.path = "/";
        }
    }

    public void canonicalize(ParsedUrl url) {
        removeLeadingTrailingJunk(url);
        removeTabsAndNewlines(url);
        lowercaseScheme(url);
        fixBackslashes(url);
        normalizePathDots(url);
        decodePath2e(url);
        pctEncodePath(url);
        emptyPathToSlash(url);
    }
}