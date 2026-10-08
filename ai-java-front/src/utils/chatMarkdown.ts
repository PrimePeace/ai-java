/** 先转义再套格式，避免回答内容被当成 HTML。 */
function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderInline(text: string): string {
  return text
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
}

/**
 * 把模型返回的 Markdown 转成对话气泡里用的 HTML。
 * 只覆盖问答里常见的标题、列表、加粗和代码，不引入额外依赖。
 */
function withStreamCursor(html: string): string {
  const cursor = '<span class="cursor">▍</span>';
  if (!html) return cursor;
  const withCursor = html.replace(
    /<\/(p|li|h[1-3]|code)>(?![\s\S]*<\/(p|li|h[1-3]|code)>)/,
    `${cursor}</$1>`,
  );
  return withCursor === html ? html + cursor : withCursor;
}

export function renderChatMarkdown(source: string, streaming = false): string {
  const lines = escapeHtml(source).replace(/\r\n/g, "\n").split("\n");
  const html: string[] = [];
  let listTag: "ul" | "ol" | null = null;

  function closeList() {
    if (!listTag) return;
    html.push(`</${listTag}>`);
    listTag = null;
  }

  function openList(tag: "ul" | "ol") {
    if (listTag === tag) return;
    closeList();
    html.push(`<${tag}>`);
    listTag = tag;
  }

  let index = 0;
  while (index < lines.length) {
    const line = lines[index] ?? "";

    if (line.startsWith("```")) {
      closeList();
      const code: string[] = [];
      index += 1;
      while (index < lines.length) {
        const codeLine = lines[index] ?? "";
        if (codeLine.startsWith("```")) break;
        code.push(codeLine);
        index += 1;
      }
      if (index < lines.length) index += 1;
      html.push(`<pre><code>${code.join("\n")}</code></pre>`);
      continue;
    }

    const heading = /^(#{1,3})\s*(.+)$/.exec(line);
    const marks = heading?.[1];
    const title = heading?.[2];
    if (marks && title) {
      closeList();
      const level = marks.length;
      html.push(`<h${level}>${renderInline(title)}</h${level}>`);
      index += 1;
      continue;
    }

    const unordered = /^[-*]\s+(.+)$/.exec(line)?.[1];
    if (unordered) {
      openList("ul");
      html.push(`<li>${renderInline(unordered)}</li>`);
      index += 1;
      continue;
    }

    const ordered = /^\d+[.、]\s*(.+)$/.exec(line)?.[1];
    if (ordered) {
      openList("ol");
      html.push(`<li>${renderInline(ordered)}</li>`);
      index += 1;
      continue;
    }

    if (line.trim() === "") {
      closeList();
      index += 1;
      continue;
    }

    closeList();
    html.push(`<p>${renderInline(line)}</p>`);
    index += 1;
  }

  closeList();
  const body = html.join("");
  return streaming ? withStreamCursor(body) : body;
}
