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
import { trackingService } from '../service/trackingService';
import { errorMessage, formatPrice } from '../utils/format';

import { anhTheoPhienBan, danhSachAnhTheoPhienBan } from '../utils/image';
import { productStatus } from '../utils/stock';
import { SPEC_FIELDS, specValue } from '../utils/specs';
import {
  isProductDiscontinued,
  isVariantDiscontinued,
  isPurchasable,
  getDefaultSelectedVariant,
  DISCONTINUED_MESSAGES,
  isVariantPendingReview,
} from '../utils/discontinuedProductRules';

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
  const lastTrackedProductRef = useRef(null);
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
      const initial = getDefaultSelectedVariant(sp);
      setMaPhienBan(initial?.maPhienBan || '');
      setSoLuong(1);
    }
    if (sp?.maSanPham && lastTrackedProductRef.current !== sp.maSanPham) {
      lastTrackedProductRef.current = sp.maSanPham;
      trackingService.trackView(sp.maSanPham);
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

  const isNgungKD = isProductDiscontinued(sp);
  const pbs = sp.phienBans || [];
  const phienBan = pbs.find((p) => p.maPhienBan === maPhienBan) || getDefaultSelectedVariant(sp) || pbs[0] || null;
  const isVarNgungKD = isVariantDiscontinued(phienBan);
  const isPending = isVariantPendingReview(phienBan);
  const isBlocked = phienBan?.trangThaiPheDuyet === 'BLOCKED';
  const conHang = isPurchasable(sp, phienBan);
  const canBuy = conHang && !isNgungKD && !isVarNgungKD && !isPending && !isBlocked && phienBan;
  // 2B.7D: backend chi tra gia cong bo; khong co -> null (khong bao gio dung 0 lam gia).
  const giaCongBo = phienBan ? (phienBan.giaBan ?? null) : (sp.giaBan ?? null);
  const giaBan = Number(giaCongBo ?? 0);

  const themVaoGio = () => {
    if (!canBuy) return;
    return run(async () => {
      await add(sp, phienBan, soLuong);
      trackingService.trackAddToCart(sp.maSanPham, phienBan.maPhienBan);
    }, 'Đã thêm vào giỏ hàng');
  };

  const muaNgay = () => {
    if (!canBuy) return;
    navigate('/checkout', { state: { mua: phienBan.maPhienBan, sl: soLuong } });
  };

  const conTheBan = (pv) => isPurchasable(sp, pv);
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

  const buttonTooltip = isNgungKD
    ? 'Sản phẩm đã ngừng kinh doanh'
    : isVarNgungKD
    ? 'Phiên bản đã ngừng kinh doanh'
    : isBlocked
    ? 'Phiên bản bị khóa bán'
    : isPending
    ? 'Sản phẩm đang chờ duyệt, tạm ngừng nhận đơn'
    : !conHang
    ? 'Sản phẩm tạm hết hàng'
    : 'Thêm vào giỏ';

  const buyNowTooltip = isNgungKD
    ? 'Sản phẩm đã ngừng kinh doanh'
    : isVarNgungKD
    ? 'Phiên bản đã ngừng kinh doanh'
    : isBlocked
    ? 'Phiên bản bị khóa bán'
    : isPending
    ? 'Sản phẩm đang chờ duyệt, tạm ngừng nhận đơn'
    : !conHang
    ? 'Sản phẩm tạm hết hàng'
    : 'Mua ngay';

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
    {isNgungKD && (
      <div className="alert alert-warning discontinued-product-banner" style={{ margin: '16px 0', padding: '14px 18px', background: '#fff3cd', color: '#856404', border: '1px solid #ffeeba', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Icon name="alert-triangle" size={22} />
          <span><strong>{DISCONTINUED_MESSAGES.DETAIL_BANNER}</strong></span>
        </div>
        <Link to="/" className="btn btn-warning btn-sm" style={{ fontWeight: 600 }}>Xem danh mục đang bán</Link>
      </div>
    )}
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
        <div className="price big">
          {isPending ? (
            <div>
              <span style={{ color: '#b45309', fontWeight: 700, fontSize: '1.5rem' }}>Giá đang cập nhật</span>
              <span style={{ marginLeft: '12px', fontSize: '0.95rem', color: '#8a5a00', fontWeight: 600 }}>
                (Tạm ngừng nhận đơn)
              </span>
            </div>
          ) : isBlocked ? (
            <div>
              <span style={{ color: '#b91c1c', fontWeight: 700, fontSize: '1.5rem' }}>Không khả dụng</span>
              <span style={{ marginLeft: '12px', fontSize: '0.95rem', color: '#a1241c', fontWeight: 600 }}>
                (Bị khóa bán)
              </span>
            </div>
          ) : giaCongBo == null ? (
            <div>
              <span style={{ color: '#b45309', fontWeight: 700, fontSize: '1.5rem' }}>
                {isNgungKD || isVarNgungKD ? 'Ngừng kinh doanh' : 'Giá đang cập nhật'}
              </span>
            </div>
          ) : (
            <>
              {formatPrice(giaBan)}
              {isNgungKD ? (
                <span style={{ marginLeft: "12px", fontSize: "0.9rem", color: "#a1241c", fontWeight: 600 }}>
                  {DISCONTINUED_MESSAGES.PRICE_NOTE}
                </span>
              ) : (
                phienBan?.giaNiemYet && Number(phienBan.giaNiemYet) > giaBan ? (
                  <span style={{ marginLeft: "12px", textDecoration: "line-through", color: "#666" }}>
                    {formatPrice(phienBan.giaNiemYet)}
                  </span>
                ) : null
              )}
            </>
          )}
        </div>
        {pbs.length > 0 && (
          <>
            {dungLuongs.length > 0 && (
              <div className="variant-picker">
                <b>Dung lượng</b>
                <div className="variant-options">
                  {dungLuongs.map((dl) => {
                    const active = phienBan?.ram === dl.ram && phienBan?.boNho === dl.boNho;
                    const listDl = pbs.filter((pv) => pv.ram === dl.ram && pv.boNho === dl.boNho);
                    const conHangDl = listDl.some(conTheBan);
                    const allNgungKD = listDl.every(isVariantDiscontinued);
                    const allPendingDl = listDl.every((pv) => isVariantPendingReview(pv));
                    const allBlockedDl = listDl.every((pv) => pv.trangThaiPheDuyet === 'BLOCKED');
                    const clickable = conHangDl || isNgungKD || allPendingDl || allBlockedDl;

                    return (
                      <button
                        key={`${dl.ram}-${dl.boNho}`}
                        type="button"
                        className={`variant-option ${active ? 'active' : ''} ${clickable ? '' : 'disabled'}`}
                        onClick={() => chonDungLuong(dl)}
                        disabled={!clickable}
                      >
                        <b>{dl.ram}GB / {dl.boNho}GB</b>
                        {!isNgungKD && allNgungKD && <small className="muted"> (Ngừng KD)</small>}
                        {!isNgungKD && !allNgungKD && allBlockedDl && <small className="muted"> (Khóa bán)</small>}
                        {!isNgungKD && !allNgungKD && !allBlockedDl && allPendingDl && <small className="muted"> (Chờ duyệt)</small>}
                        {!isNgungKD && !allNgungKD && !allBlockedDl && !allPendingDl && !conHangDl && <small className="muted"> (Hết hàng)</small>}
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
                    const varNgungKD = isVariantDiscontinued(pv);
                    const varPending = isVariantPendingReview(pv);
                    const varBlocked = pv?.trangThaiPheDuyet === 'BLOCKED';
                    const hetHangMau = !isNgungKD && !conTheBan(pv) && !varPending && !varBlocked;

                    let titleText = mau;
                    if (isNgungKD) titleText = 'Sản phẩm ngừng kinh doanh';
                    else if (varNgungKD) titleText = 'Phiên bản ngừng kinh doanh';
                    else if (varBlocked) titleText = 'Phiên bản bị khóa bán';
                    else if (varPending) titleText = 'Phiên bản đang chờ duyệt';
                    else if (hetHangMau) titleText = 'Hết hàng';

                    return (
                      <button
                        key={mau}
                        type="button"
                        className={`variant-option ${active ? 'active' : ''} ${hetHangMau ? 'disabled' : ''}`}
                        onClick={() => chonMauSac(mau)}
                        title={titleText}
                        disabled={!isNgungKD && varNgungKD}
                      >
                        <span>{mau}</span>
                        {!isNgungKD && varNgungKD && <small className="muted"> (Ngừng KD)</small>}
                        {!isNgungKD && !varNgungKD && varBlocked && <small className="muted"> (Khóa)</small>}
                        {!isNgungKD && !varNgungKD && !varBlocked && varPending && <small className="muted"> (Chờ duyệt)</small>}
                        {!isNgungKD && !varNgungKD && !varBlocked && !varPending && hetHangMau && <small className="muted"> (Hết hàng)</small>}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </>
        )}
        <p>
          {isNgungKD ? (
            <StatusBadge status="NGUNG_KINH_DOANH" />
          ) : isVarNgungKD ? (
            <>
              <StatusBadge status="NGUNG_KINH_DOANH" />
              <span className="muted" style={{ marginLeft: '8px' }}>Phiên bản ngừng kinh doanh</span>
            </>
          ) : isPending ? (
            <>
              <StatusBadge status="PENDING_REVIEW" />
              <span className="muted" style={{ marginLeft: '8px', color: '#8a5a00' }}>Tạm ngừng nhận đơn</span>
            </>
          ) : isBlocked ? (
            <>
              <StatusBadge status="BLOCKED" />
              <span className="muted" style={{ marginLeft: '8px', color: '#a1241c' }}>Không khả dụng</span>
            </>
          ) : (
            <>
              <StatusBadge status={conHang ? "CON_HANG" : "HET_HANG"} />
              {!conHang && <span className="muted" style={{ marginLeft: '8px' }}>Hết hàng</span>}
            </>
          )}
        </p>
        <div className="buy-box" ref={boxRef}>
          <div className="buy-main">
            <input
              type="number"
              min="1"
              max={phienBan?.soLuongTon || 1}
              value={soLuong}
              onChange={(e) => setSoLuong(Number(e.target.value))}
              className="qty"
              aria-label="Số lượng"
              disabled={!canBuy}
            />
            <button
              className="btn btn-primary btn-lg"
              disabled={!canBuy}
              onClick={themVaoGio}
              title={buttonTooltip}
            >
              Thêm vào giỏ
            </button>
            {isCustomer && (
              <button
                className="btn btn-buy btn-lg"
                disabled={!canBuy}
                onClick={muaNgay}
                title={buyNowTooltip}
              >
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
        <div className="sticky-name">
          <b>{sp.tenMay}</b>
          <span className="muted">
            {isPending || giaCongBo == null ? 'Giá đang cập nhật' : isBlocked ? 'Không khả dụng' : `Đơn giá ${formatPrice(giaBan)}`}
          </span>
        </div>
        <div className="sticky-total">
          <span className="muted">Tạm tính</span>
          <b>{isPending || giaCongBo == null ? 'Đang cập nhật' : isBlocked ? 'Không khả dụng' : formatPrice(giaBan * (soLuong || 1))}</b>
        </div>
        <div className="sticky-actions">
          <button
            className="btn"
            disabled={!canBuy}
            tabIndex={showBar ? 0 : -1}
            onClick={themVaoGio}
            title={buttonTooltip}
          >
            <Icon name="cart" size={18} /> Thêm vào giỏ
          </button>
          {isCustomer && (
            <button
              className="btn btn-primary"
              disabled={!canBuy}
              tabIndex={showBar ? 0 : -1}
              onClick={muaNgay}
              title={buyNowTooltip}
            >
              Mua ngay
            </button>
          )}
        </div>
      </div>
    </div>
    </>
  );
}
