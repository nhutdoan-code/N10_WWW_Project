export default function CategoryTree({ categories, value, onChange, parent = null }) {
  const nodes = categories.filter((c) => (c.maLoaiCha || null) === parent);
  if (nodes.length === 0) return null;

  return (
    <ul className="tree">
      {parent === null && (
        <li>
          <button className={value === '' ? 'tree-item active' : 'tree-item'} onClick={() => onChange('')}>Tất cả sản phẩm</button>
        </li>
      )}
      {nodes.map((c) => (
        <li key={c.maLoai}>
          <button className={value === c.maLoai ? 'tree-item active' : 'tree-item'} onClick={() => onChange(c.maLoai)}>{c.tenLoai}</button>
          <CategoryTree categories={categories} value={value} onChange={onChange} parent={c.maLoai} />
        </li>
      ))}
    </ul>
  );
}
