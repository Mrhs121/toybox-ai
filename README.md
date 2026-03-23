# toybox-ai

Toys written by ai

用 ai 写的一些小玩具。

## FastTerminal

Android SSH terminal optimized for external keyboards and mice.

- Source: `./fastTerminal/`
- APK: `./fastTerminal/fastTerminal-debug.apk`

Features:

- `Esc` is terminal-first and does not trigger Android exit.
- Mouse drag selection and right-click paste are supported in the terminal view.
- Multiple SSH connections can be saved and managed locally.
- SSH sessions can stay alive in the background.

## Codex 对话记录查看器

[codex-chat-viewer.html](./codex-chat-viewer.html)

Codex Desktop 的会话文件保存在本地 `~/.codex/sessions/` 目录下，但官方客户端无法直接导出或分享完整的对话内容。这个小工具可以直接加载这些 JSONL 文件，将对话以清晰、美观的界面呈现出来，并支持搜索、过滤和 Markdown 渲染。

![预览图](./img/codex-chat-viewer.png)
