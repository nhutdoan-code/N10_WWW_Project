export function flattenTree(categories = [], parent = null, depth = 0) {
  return categories
    .filter((c) => (c.maLoaiCha || null) === parent)
    .flatMap((c) => [{ ...c, depth }, ...flattenTree(categories, c.maLoai, depth + 1)]);
}

export const categoryLabel = (c) => `${'— '.repeat(c.depth)}${c.tenLoai}`;
