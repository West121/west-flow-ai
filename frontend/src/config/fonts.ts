/**
 * 可用字体名称列表（在 `/settings/appearance` 页面中使用）。
 * 这个数组会被用于生成动态字体类名，例如 `font-inter`、`font-manrope`。
 *
 * 📝 添加新字体的步骤（Tailwind v4+）：
 * 1. 在这里加入字体名称。
 * 2. 更新 `index.html` 中的 `<link>` 标签，引入新的字体资源。
 * 3. 在 `index.css` 中通过 `@theme inline` 和 `font-family` 变量补充新的字体族。
 *
 * 示例：
 * `fonts.ts`           → 在数组中加入 `roboto`。
 * `index.html`         → 为 Roboto 添加 Google Fonts 链接。
 * `index.css`          → 在 CSS 中添加新的字体，例如：
 *   @theme inline {
 *      // 其他字体族
 *      --font-roboto: 'Roboto', var(--font-sans);
 *   }
 */
export const fonts = ['inter', 'manrope', 'system'] as const
