import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import useDebounce from '../hooks/useDebounce';
import Icon from './Icon';
import ProductImage from './ProductImage';
import { productService } from '../service/productService';
import { trackingService } from '../service/trackingService';
import { formatPrice } from '../utils/format';

export default function SearchBox() {
  const [text, setText] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const debounced = useDebounce(text.trim(), 300);
  const box = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!debounced) {
      setResults([]);
      return;
    }
    let alive = true;
    setLoading(true);
    productService.search(debounced)
      .then((data) => alive && setResults(data))
      .catch(() => alive && setResults([]))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [debounced]);

  useEffect(() => {
    const close = (e) => !box.current?.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const submit = (e) => {
    e.preventDefault();
    const query = text.trim();
    if (query) {
      trackingService.trackSearch(query, 'SEARCH_SUBMIT');
    }
    setOpen(false);
    navigate(`/?q=${encodeURIComponent(query)}`);
  };

  return (
    <div className="search" ref={box}>
      <form onSubmit={submit}>
        <Icon name="search" size={18} />
        <input
          value={text}
          placeholder="Tìm tên máy, mã máy, hãng..."
          onChange={(e) => {
            setText(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
        />
      </form>
      {open && debounced && (
        <div className="search-dropdown">
          {loading && <div className="muted pad">Đang tìm...</div>}
          {!loading && results.length === 0 && <div className="muted pad">Không tìm thấy sản phẩm</div>}
          {results.map((sp) => (
            <Link
              key={sp.maSanPham}
              to={`/products/${sp.maSanPham}`}
              onClick={() => {
                trackingService.trackClick(sp.maSanPham, 'SEARCH_DROPDOWN');
                setOpen(false);
              }}
            >
              <ProductImage src={sp.anhChinh} alt="" />
              <span>{sp.tenMay}</span>
              <b>{formatPrice(sp.giaBan)}</b>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
