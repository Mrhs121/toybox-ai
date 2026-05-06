import Foundation
import cmark_gfm
import cmark_gfm_extensions

struct MarkdownParser {
    static func renderHTML(from markdown: String) -> String {
        guard let cString = markdown.cString(using: .utf8) else {
            return "<p>Encoding error</p>"
        }

        cmark_gfm_core_extensions_ensure_registered()

        let parser = cmark_parser_new(CMARK_OPT_DEFAULT)

        let extensionNames = ["table", "strikethrough", "autolink", "tagfilter", "tasklist"]
        for name in extensionNames {
            if let ext = cmark_find_syntax_extension(name) {
                cmark_parser_attach_syntax_extension(parser, ext)
            }
        }

        cmark_parser_feed(parser, cString, strlen(cString))
        let doc = cmark_parser_finish(parser)

        guard let doc else {
            cmark_parser_free(parser)
            return "<p>Parse error</p>"
        }

        guard let html = cmark_render_html(doc, CMARK_OPT_UNSAFE, nil) else {
            cmark_node_free(doc)
            cmark_parser_free(parser)
            return "<p>Render error</p>"
        }

        var result = String(cString: html)

        cmark_node_free(doc)
        cmark_parser_free(parser)

        // Add id attributes to headings for TOC navigation
        result = addHeadingIDs(to: result)

        return result
    }

    private static func addHeadingIDs(to html: String) -> String {
        let pattern = "<(h[1-6])>(.*?)</\\1>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators]) else {
            return html
        }

        let nsString = html as NSString
        let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsString.length))

        var result = html

        // Process in reverse to preserve ranges
        for match in matches.reversed() {
            let tag = nsString.substring(with: match.range(at: 1))
            let content = nsString.substring(with: match.range(at: 2))
            let anchor = makeAnchor(content)
            let fullRange = match.range(at: 0)

            let replacement = "<\(tag) id=\"\(anchor)\">\(content)</\(tag)>"
            result = (result as NSString).replacingCharacters(in: fullRange, with: replacement)
        }

        return result
    }

    static func makeAnchor(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespaces)
            .lowercased()
            .replacingOccurrences(of: "\\s+", with: "-", options: .regularExpression)
            .replacingOccurrences(of: "[^a-z0-9\\u{4e00}-\\u{9fff}\\-]", with: "", options: .regularExpression)
            .replacingOccurrences(of: "^-+|-+$", with: "", options: .regularExpression)
    }

    static func extractTOC(from markdown: String) -> [TOCItem] {
        var items: [TOCItem] = []
        let lines = markdown.components(separatedBy: .newlines)

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard trimmed.hasPrefix("#") else { continue }

            var level = 0
            for char in trimmed {
                if char == "#" {
                    level += 1
                } else {
                    break
                }
            }

            guard level >= 1 && level <= 6 else { continue }
            guard trimmed.count > level, trimmed[trimmed.index(trimmed.startIndex, offsetBy: level)] == " " else { continue }

            let text = String(trimmed.dropFirst(level + 1)).trimmingCharacters(in: .whitespaces)
            guard !text.isEmpty else { continue }

            let anchor = makeAnchor(text)
            items.append(TOCItem(text: text, level: level, anchor: anchor))
        }

        return items
    }
}
