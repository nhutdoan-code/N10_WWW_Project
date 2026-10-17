import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import useDebounce from '../hooks/useDebounce';
import { productService } from '../service/productService';
import { formatPrice } from '../utils/format';

export default function SearchBox() {
  const [text, setText] = useState('');
  const [ai, setAi] = useState(false);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const debounced = useDebounce(text.trim(), ai ? 700 : 300);
  const box = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!debounced) {
      setResults([]);
      return;
    }
    let alive = true;
    setLoading(true);
    (ai ? productService.aiSearch : productService.search)(debounced)
      .then((data) => alive && setResults(data))
      .catch(() => alive && setResults([]))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [debounced, ai]);

  useEffect(() => {
    const close = (e) => !box.current?.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const submit = (e) => {
    e.preventDefault();
    setOpen(false);
    navigate(`/?q=${encodeURIComponent(text.trim())}${ai ? '&ai=1' : ''}`);
  };

  return (
    <div className="search" ref={box}>
      <form onSubmit={submit}>
        <input
          value={text}
          placeholder={ai ? 'Mô tả nhu cầu: "điện thoại pin trâu dưới 10 triệu"' : 'Tìm tên máy, mã máy, hãng...'}
          onChange={(e) => {
            setText(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
        />
        <label className="ai-toggle">
          <input type="checkbox" checked={ai} onChange={(e) => setAi(e.target.checked)} /> AI
        </label>
      </form>
      {open && debounced && (
        <div className="search-dropdown">
          {loading && <div className="muted pad">Đang tìm...</div>}
          {!loading && results.length === 0 && <div className="muted pad">Không tìm thấy sản phẩm</div>}
          {results.map((sp) => (
            <Link key={sp.maSanPham} to={`/products/${sp.maSanPham}`} onClick={() => setOpen(false)}>
              <img src={sp.anhChinh} alt="" />
              <span>{sp.tenMay}</span>
              <b>{formatPrice(sp.giaBan)}</b>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
