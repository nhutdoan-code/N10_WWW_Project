import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Icon from '../components/Icon';
import ProductCard from '../components/ProductCard';
import ProductImage from '../components/ProductImage';
import QuestionSection from '../components/QuestionSection';
import ReviewSection from '../components/ReviewSection';
import StatusBadge from '../components/StatusBadge';
import useAuth from '../hooks/useAuth';
import useCart from '../hooks/useCart';
import useCompare from '../hooks/useCompare';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { productService } from '../service/productService';
import { wishlistService } from '../service/wishlistService';
import { errorMessage, formatPrice } from '../utils/format';

import { anhTheoPhienBan, danhSachAnhTheoPhienBan } from '../utils/image';
import { productStatus } from '../utils/stock';
import { SPEC_FIELDS, specValue } from '../utils/specs';

const COMMITS = [
  { icon: 'shield', title: 'Hàng chính hãng', text: 'Nguyên seal, bảo hành theo chính sách của hãng.' },
  { icon: 'refresh', title: 'Đổi trả ngay trên web', text: 'Gửi yêu cầu đổi trả hoặc hoàn tiền và theo dõi trạng thái xử lý.' },
  { icon: 'truck', title: 'Phí giao hàng rõ ràng', text: 'Phí theo khu vực, hiển thị trước khi đặt; miễn phí khi đủ điều kiện.' },
  { icon: 'gift', title: 'Voucher tự động', text: 'Hệ thống tự chọn mã giảm giá tốt nhất ở bước thanh toán.' },
];

export default function ProductDetailPage() {
  const { ma } = useParams();
  const navigate = useNavigate();
  const { isCustomer } = useAuth();
  const { add } = useCart();
  const { ids, toggle } = useCompare();
  const toast = useToast();
  const [image, setImage] = useState(null);
  const [soLuong, setSoLuong] = useState(1);
  const [maPhienBan, setMaPhienBan] = useState('');
  const { data: sp, loading, error } = useFetch(() => productService.get(ma), [ma]);
  const { data: related } = useFetch(() => productService.related(ma), [ma]);

  const boxRef = useRef(null);
  const [showBar, setShowBar] = useState(false);
  useEffect(() => {
    const el = boxRef.current;
    if (!el) return undefined;
    const io = new IntersectionObserver(([e]) => setShowBar(!e.isIntersecting && e.boundingClientRect.top < 0));
    io.observe(el);
    return () => io.disconnect();
  }, [sp]);

  useEffect(() => {
    if (sp?.phienBans?.length) {
      // Chỉ chọn trong các variant còn kinh doanh; không fallback sang NGUNG_KINH_DOANH.
      const active = sp.phienBans.filter((p) => p.trangThai === 'DANG_KINH_DOANH');
      const first = active.find((p) => p.soLuongTon > 0) || active[0];
      setMaPhienBan(first?.maPhienBan || '');
      setSoLuong(1);
    }
  }, [sp?.maSanPham]);
  // Đổi phiên bản/màu thì bỏ ảnh đang chọn tay để hiện ảnh đúng màu mới
  useEffect(() => {
    setImage(null);
  }, [maPhienBan]);

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
    } catch (e) {
      toast(e.response ? errorMessage(e) : e.message, 'error');
    }
  };

  // Chỉ hiển thị tổ hợp thực sự tồn tại trong DB và còn kinh doanh, không tạo combination giả
  const pbs = (sp.phienBans || []).filter((p) => p.trangThai === 'DANG_KINH_DOANH');

  const phienBan = pbs.find((p) => p.maPhienBan === maPhienBan) || pbs[0] || null;
  const conHang = phienBan?.trangThai === 'DANG_KINH_DOANH' && phienBan.soLuongTon > 0;
  const giaBan = Number(phienBan?.giaBan ?? sp.giaBan);
  const themVaoGio = () => { if(!phienBan || !conHang) return; return run(() => add(sp, phienBan, soLuong), 'Đã thêm vào giỏ hàng'); };
  // Buy Now: truyền variant qua React Router state (không lộ ID trên URL).
  // state nằm trong history nên vẫn còn khi refresh; query vẫn được CheckoutPage
  // chấp nhận để tương thích link cũ.
  const muaNgay = () => { if(!phienBan || !conHang) return; navigate('/checkout', { state: { mua: phienBan.maPhienBan, sl: soLuong } }); };
  const conTheBan = (pv) => pv && pv.trangThai === 'DANG_KINH_DOANH' && Number(pv.soLuongTon) > 0;
  const keyDungLuong = (pv) => `${pv.ram}-${pv.boNho}`;
  const curKey = phienBan ? keyDungLuong(phienBan) : null;
  const dungLuongs = [...new Map(pbs.map((pv) => [keyDungLuong(pv), { ram: pv.ram, boNho: pv.boNho }])).values()];
  const mauSacs = curKey
    ? [...new Set(pbs.filter((pv) => keyDungLuong(pv) === curKey).map((pv) => pv.mauSac).filter(Boolean))]
    : [];
  const chonDungLuong = (dl) => {
    const list = pbs.filter((pv) => pv.ram === dl.ram && pv.boNho === dl.boNho);
    const sel = list.find((pv) => conTheBan(pv) && pv.mauSac === phienBan?.mauSac) || list.find(conTheBan) || list[0];
    if (!sel) return;
    setMaPhienBan(sel.maPhienBan);
    setSoLuong(1);
  };
  const chonMauSac = (mau) => {
    const sel = pbs.find((pv) => pv.ram === phienBan?.ram && pv.boNho === phienBan?.boNho && pv.mauSac === mau);
    if (!sel) return;
    setMaPhienBan(sel.maPhienBan);
    setSoLuong(1);
  };
  const dsAnh = danhSachAnhTheoPhienBan(sp, phienBan);
  const anhHienThi = image && dsAnh.some((a) => a.duongDan === image)
      ? image
      : anhTheoPhienBan(sp, phienBan);
  return (
    <>
    <nav className="crumbs" aria-label="Đường dẫn">
      <Link to="/">Trang chủ</Link><span>›</span>
      <Link to={`/?loai=${sp.maLoai}`}>{sp.tenLoai}</Link><span>›</span>
      <b>{sp.tenMay}</b>
    </nav>
    <div className="detail">
      <div>
        <ProductImage className="detail-img" src={anhHienThi} alt={sp.tenMay} />
        <div className="thumbs">
          {dsAnh.map((anh) => {
            const url = anh.duongDan;
            return <ProductImage key={anh.maHinhAnh || url} src={url} alt="" onClick={() => setImage(url)} />;
          })}
        </div>
        <section className="desc">
          <h3>Mô tả sản phẩm</h3>
          {(sp.moTa || 'Đang cập nhật mô tả cho sản phẩm này.').split(/\n\s*\n/).map((t) => <p key={t}>{t}</p>)}
        </section>
        <section className="commit">
          <h3>PhoneNest cam kết</h3>
          <ul>
            {COMMITS.map((c) => (
              <li key={c.title}>
                <Icon name={c.icon} size={22} />
                <span><b>{c.title}</b>{c.text}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
      <div>
        <h1>{sp.tenMay}</h1>
        <p className="muted">Hãng: {sp.tenThuongHieu || sp.tenLoai}</p>
        <div className="price big">{formatPrice(giaBan)}{phienBan?.giaNiemYet && Number(phienBan.giaNiemYet) > giaBan ? <span style={{marginLeft:"12px",textDecoration:"line-through",color:"#666"}}>{formatPrice(phienBan.giaNiemYet)}</span> : null}</div>
        {pbs.length > 0 && (
          <>
            {dungLuongs.length > 0 && (
              <div className="variant-picker">
                <b>Dung lượng</b>
                <div className="variant-options">
                  {dungLuongs.map((dl) => {
                    const active = phienBan?.ram === dl.ram && phienBan?.boNho === dl.boNho;
                    const conHangDl = pbs.some((pv) => pv.ram === dl.ram && pv.boNho === dl.boNho && conTheBan(pv));
                    return (
                      <button
                        key={`${dl.ram}-${dl.boNho}`}
                        type="button"
                        className={`variant-option ${active ? 'active' : ''} ${conHangDl ? '' : 'disabled'}`}
                        onClick={() => chonDungLuong(dl)}
                        disabled={!conHangDl}
                      >
                        <b>{dl.ram}GB / {dl.boNho}GB</b>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
            {mauSacs.length > 0 && (
              <div className="variant-picker">
                <b>Màu sắc</b>
                <div className="variant-options">
                  {mauSacs.map((mau) => {
                    const pv = pbs.find((x) => x.ram === phienBan?.ram && x.boNho === phienBan?.boNho && x.mauSac === mau);
                    const active = pv?.maPhienBan === phienBan?.maPhienBan;
                    const hetHangMau = !conTheBan(pv);
                    return (
                      <button
                        key={mau}
                        type="button"
                        className={`variant-option ${active ? 'active' : ''} ${hetHangMau ? 'disabled' : ''}`}
                        onClick={() => chonMauSac(mau)}
                        title={hetHangMau ? 'Hết hàng' : mau}
                        disabled={hetHangMau}
                      >
                        <span>{mau}</span>
                        {hetHangMau && <small className="muted"> (Hết hàng)</small>}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </>
        )}
        <p><StatusBadge status={conHang ? "CON_HANG" : "HET_HANG"} /> {!conHang && <span className="muted">Hết hàng</span>}</p>
        <div className="buy-box" ref={boxRef}>
          <div className="buy-main">
            <input type="number" min="1" max={phienBan?.soLuongTon || 1} value={soLuong} onChange={(e) => setSoLuong(Number(e.target.value))} className="qty" aria-label="Số lượng" />
            <button className="btn btn-primary btn-lg" disabled={!conHang || !phienBan} onClick={themVaoGio}>
              Thêm vào giỏ
            </button>
            {isCustomer && (
              <button className="btn btn-buy btn-lg" disabled={!conHang || !phienBan} onClick={muaNgay}>
                Mua ngay
              </button>
            )}
          </div>
          <div className="buy-sub">
            {isCustomer && (
              <button className="btn" onClick={() => run(() => wishlistService.add(sp.maSanPham), 'Đã thêm vào yêu thích')}>
                <Icon name="heart" size={16} /> Yêu thích
              </button>
            )}
            <button className="btn" onClick={() => run(() => toggle(sp.maSanPham), ids.includes(sp.maSanPham) ? 'Đã bỏ khỏi so sánh' : 'Đã thêm vào so sánh')}>
              <Icon name="compare" size={16} /> {ids.includes(sp.maSanPham) ? 'Bỏ so sánh' : 'So sánh'}
            </button>
          </div>
        </div>
        <h3>Thông số kỹ thuật</h3>
        <ul className="specs">
          {SPEC_FIELDS.filter((f) => specValue(f, sp.thongSo) !== '—').map((f) => (
            <li key={f.key}><span>{f.label}</span><b>{specValue(f, sp.thongSo)}</b></li>
          ))}
        </ul>
      </div>
    </div>
    <ReviewSection ma={ma} />
    <QuestionSection ma={ma} />
    {related?.length > 0 && (
      <section>
        <h3>Sản phẩm liên quan</h3>
        <div className="grid">{related.map((r) => <ProductCard key={r.maSanPham} sp={r} />)}</div>
      </section>
    )}
    <div className="sticky-spacer no-print" />
    <div className={showBar ? 'sticky-bar on no-print' : 'sticky-bar no-print'} aria-hidden={!showBar}>
      <div className="container sticky-inner">
        <ProductImage src={anhHienThi} alt="" />
        <div className="sticky-name"><b>{sp.tenMay}</b><span className="muted">Đơn giá {formatPrice(giaBan)}</span></div>
        <div className="sticky-total">
          <span className="muted">Tạm tính</span>
          <b>{formatPrice(giaBan * (soLuong || 1))}</b>
        </div>
        <div className="sticky-actions">
          <button className="btn" disabled={!conHang || !phienBan} tabIndex={showBar ? 0 : -1} onClick={themVaoGio}><Icon name="cart" size={18} /> Thêm vào giỏ</button>
          {isCustomer && <button className="btn btn-primary" disabled={!conHang || !phienBan} tabIndex={showBar ? 0 : -1} onClick={muaNgay}>Mua ngay</button>}
        </div>
      </div>
    </div>
    </>
  );
}
