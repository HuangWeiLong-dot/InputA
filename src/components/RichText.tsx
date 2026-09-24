import React from 'react';
import { parseInlineMarkdown, splitOnWord } from '../utils/inlineMarkdown';

interface RichTextProps {
  text: string;
  /**
   * 额外标出的词（例句里被查的那个）。它先被切成标记段，再走 Markdown 解析 ——
   * 顺序反过来的话，一个落在 `**…**` 内部的词会被拆坏标记。
   */
  highlight?: string;
  className?: string;
}

/**
 * 渲染带 `**粗体**` / `` `代码` `` 的文本。
 *
 * 刻意不用 dangerouslySetInnerHTML：这些字符串来自模型输出（也可能来自用户
 * 手改的备份），是不可信输入。解析成 token 再渲染，注入面为零。
 */
export const RichText: React.FC<RichTextProps> = ({ text, highlight, className }) => (
  <span className={className}>
    {splitOnWord(text, highlight ?? '').map((part, partIdx) => {
      if (part.match) {
        return (
          <strong key={partIdx} className="font-semibold text-[var(--text-strong)]">
            {part.text}
          </strong>
        );
      }

      return (
        <React.Fragment key={partIdx}>
          {parseInlineMarkdown(part.text).map((token, tokenIdx) => {
            if (token.kind === 'bold') {
              return (
                <strong key={tokenIdx} className="font-semibold text-[var(--text-strong)]">
                  {token.text}
                </strong>
              );
            }
            if (token.kind === 'code') {
              return (
                <code
                  key={tokenIdx}
                  className="border border-[var(--border-color)] bg-[var(--bg-subtle)] px-1 py-0.5 font-mono text-[0.92em]"
                >
                  {token.text}
                </code>
              );
            }
            return <React.Fragment key={tokenIdx}>{token.text}</React.Fragment>;
          })}
        </React.Fragment>
      );
    })}
  </span>
);
