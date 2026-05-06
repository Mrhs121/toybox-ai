import SwiftUI
import WebKit

struct MarkdownPreviewView: NSViewRepresentable {
    let htmlContent: String
    var scrollToAnchor: String?

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeNSView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.setValue(false, forKey: "drawsBackground")
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView
        return webView
    }

    func updateNSView(_ webView: WKWebView, context: Context) {
        let fullHTML = Self.wrapInTemplate(content: htmlContent)
        webView.loadHTMLString(fullHTML, baseURL: nil)

        if let anchor = scrollToAnchor {
            context.coordinator.pendingAnchor = anchor
        }
    }

    class Coordinator: NSObject, WKNavigationDelegate {
        var webView: WKWebView?
        var pendingAnchor: String?

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard let anchor = pendingAnchor else { return }
            pendingAnchor = nil

            let js = """
            (function() {
                var el = document.getElementById('\(anchor)');
                if (el) {
                    el.scrollIntoView({behavior: 'smooth', block: 'start'});
                    return 'found';
                }
                return 'not_found';
            })();
            """
            webView.evaluateJavaScript(js)
        }
    }

    private static func wrapInTemplate(content: String) -> String {
        let template = Self.htmlTemplate()
        return template.replacingOccurrences(of: "{{CONTENT}}", with: content)
    }

    private static func htmlTemplate() -> String {
        guard let url = Bundle.main.url(forResource: "template", withExtension: "html"),
              let html = try? String(contentsOf: url, encoding: .utf8) else {
            return fallbackTemplate()
        }
        return html
    }

    private static func fallbackTemplate() -> String {
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                :root {
                    --bg: #ffffff;
                    --text: #1d1d1f;
                    --muted: #6e6e73;
                    --border: #d2d2d7;
                    --code-bg: #f5f5f7;
                    --link: #0066cc;
                    --blockquote-border: #0071e3;
                    --table-border: #d2d2d7;
                    --table-header-bg: #f5f5f7;
                }
                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg: #1c1c1e;
                        --text: #f5f5f7;
                        --muted: #98989d;
                        --border: #38383a;
                        --code-bg: #2c2c2e;
                        --link: #2997ff;
                        --blockquote-border: #0a84ff;
                        --table-border: #38383a;
                        --table-header-bg: #2c2c2e;
                    }
                }
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
                    font-size: 15px; line-height: 1.7;
                    color: var(--text); background: var(--bg);
                    padding: 32px 40px; max-width: 900px; margin: 0 auto;
                }
                h1, h2, h3, h4, h5, h6 { font-weight: 600; line-height: 1.3; margin-top: 24px; margin-bottom: 16px; scroll-margin-top: 20px; }
                h1 { font-size: 2em; border-bottom: 1px solid var(--border); padding-bottom: 8px; }
                h2 { font-size: 1.5em; border-bottom: 1px solid var(--border); padding-bottom: 6px; }
                h3 { font-size: 1.25em; }
                p { margin-bottom: 16px; }
                a { color: var(--link); text-decoration: none; }
                a:hover { text-decoration: underline; }
                strong { font-weight: 600; }
                ul, ol { margin-bottom: 16px; padding-left: 24px; }
                li { margin-bottom: 4px; }
                blockquote { border-left: 3px solid var(--blockquote-border); padding: 8px 16px; margin-bottom: 16px; color: var(--muted); background: var(--code-bg); border-radius: 0 6px 6px 0; }
                pre { background: var(--code-bg); border-radius: 8px; padding: 16px; overflow-x: auto; margin-bottom: 16px; border: 1px solid var(--border); }
                code { font-family: "SF Mono", "Menlo", "Monaco", "Courier New", monospace; font-size: 13px; }
                pre code { background: none; padding: 0; border: none; }
                :not(pre) > code { background: var(--code-bg); padding: 2px 6px; border-radius: 4px; border: 1px solid var(--border); }
                table { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
                th, td { border: 1px solid var(--table-border); padding: 8px 12px; text-align: left; }
                th { background: var(--table-header-bg); font-weight: 600; }
                hr { border: none; border-top: 1px solid var(--border); margin: 24px 0; }
                img { max-width: 100%; border-radius: 8px; margin: 8px 0; }
                input[type="checkbox"] { margin-right: 6px; vertical-align: middle; }
            </style>
        </head>
        <body>
        {{CONTENT}}
        </body>
        </html>
        """
    }
}
