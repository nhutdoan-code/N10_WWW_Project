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
import { productStatus } from '../utils/stock';
import { SPEC_FIELDS, specValue } from '../utils/specs';

const COMMITS = [
  { icon: 'shield', title: 'Hàng chính hãng', text: 'Nguyên seal, bảo hành theo chính sách của hãng.' },
  { icon: 'refresh', title: 'Đổi trả ngay trên web', text: 'Gửi yêu cầu đổi trả hoặc hoàn tiền và theo dõi trạng thái xử lý.' },
  { icon: 'truck', title: 'Phí giao hàng rõ ràng', text: 'Phí theo khu vực, hiển thị trước khi đặt; miễn phí khi đủ điều kiện.' },
  { icon: 'gift', title: 'Voucher tự động', text: 'Hệ thống tự chọn mã giảm giá tốt nhất ở bước thanh toán.' },
];

const isVariantAvailable = (pb) => {
  if (!pb) return false;
  return ['CON_HANG', 'DANG_KINH_DOANH'].includes(pb.trangThai) && Number(pb.soLuongTon) > 0;
};

const isVariantDiscontinued = (pb) => pb?.trangThai === 'NGUNG_KINH_DOANH';

export default function ProductDetailPage() {
  const { ma } = useParams();
  const navigate = useNavigate();
  const { isCustomer } = useAuth();
  const { add } = useCart();
  const { ids, toggle } = useCompare();
  const toast = useToast();

  const [image, setImage] = useState(null);
  const [soLuong, setSoLuong] = useState(1);
  const [maPhienBanChon, setMaPhienBanChon] = useState('');
  const [showBar, setShowBar] = useState(false);
  const boxRef = useRef(null);

  const { data: sp, loading, error } = useFetch(() => productService.get(ma), [ma]);
  const { data: related } = useFetch(() => productService.related(ma), [ma]);

  useEffect(() => {
    const el = boxRef.current;
    if (!el) return;

    const io = new IntersectionObserver(([entry]) => {
      setShowBar(!entry.isIntersecting && entry.boundingClientRect.top < 0);
    });

    io.observe(el);
    return () => io.disconnect();
  }, [sp]);

  useEffect(() => {
    setSoLuong(1);
    setImage(null);
    setMaPhienBanChon('');
  }, [ma]);

  useEffect(() => {
    if (!sp) return;

    const versions = Array.isArray(sp.phienBans) ? sp.phienBans : [];
    const first = versions.find(isVariantAvailable)
        || versions.find((pb) => !isVariantDiscontinued(pb))
        || versions[0];

    setMaPhienBanChon(first?.maPhienBan || '');
    setSoLuong(1);
  }, [sp]);

  if (loading) return <p className="muted">Đang tải...</p>;
  if (error) return <p className="error">{error}</p>;
  if (!sp) return <p className="error">Không tìm thấy sản phẩm.</p>;

  const phienBans = Array.isArray(sp.phienBans) ? sp.phienBans : [];
  const phienBanChon = phienBans.find(
      (pb) => String(pb.maPhienBan) === String(maPhienBanChon)
  );

  const cauHinhs = [
    ...new Map(
        phienBans.map((pb) => [
          `${pb.ram}-${pb.boNho}`,
          { ram: pb.ram, boNho: pb.boNho },
        ])
    ).values(),
  ];

  const mauSacTheoCauHinh = phienBanChon
      ? phienBans.filter(
          (pb) =>
              Number(pb.ram) === Number(phienBanChon.ram) &&
              Number(pb.boNho) === Number(phienBanChon.boNho)
      )
      : [];

  const giaBan = Number(phienBanChon?.giaBan ?? sp.giaBan ?? 0);
  const soLuongTon = Number(phienBanChon?.soLuongTon ?? 0);

  const ngungKinhDoanh =
      isVariantDiscontinued(phienBanChon) ||
      sp.trangThai === 'NGUNG_KINH_DOANH';

  const conHang =
      Boolean(phienBanChon) &&
      !ngungKinhDoanh &&
      isVariantAvailable(phienBanChon);

  const hetHang = Boolean(phienBanChon) && !conHang && !ngungKinhDoanh;

  const trangThaiHienThi = ngungKinhDoanh
      ? 'NGUNG_KINH_DOANH'
      : conHang
          ? 'CON_HANG'
          : 'HET_HANG';

  const sanPhamHienThi = {
    ...sp,
    trangThai: trangThaiHienThi,
    soLuongTon,
  };

  const chonCauHinh = (cauHinh) => {
    const versions = phienBans.filter(
        (pb) =>
            Number(pb.ram) === Number(cauHinh.ram) &&
            Number(pb.boNho) === Number(cauHinh.boNho)
    );

    const next = versions.find(isVariantAvailable)
        || versions.find((pb) => !isVariantDiscontinued(pb))
        || versions[0];

    setMaPhienBanChon(next?.maPhienBan || '');
    setSoLuong(1);
  };

  const chonMauSac = (maPB) => {
    setMaPhienBanChon(maPB);
    setSoLuong(1);
  };

  const capNhatSoLuong = (value) => {
    const sl = Math.floor(Number(value));

    if (!Number.isFinite(sl) || sl < 1 || soLuongTon <= 0) {
      setSoLuong(1);
      return;
    }

    setSoLuong(Math.min(sl, soLuongTon));
  };

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
    } catch (e) {
      toast(e.response ? errorMessage(e) : e.message, 'error');
    }
  };

  const themVaoGio = () => {
    if (!phienBanChon) {
      toast('Vui lòng chọn phiên bản sản phẩm.', 'error');
      return;
    }

    if (ngungKinhDoanh) {
      toast('Phiên bản này đã ngừng kinh doanh.', 'error');
      return;
    }

    if (!conHang) {
      toast('Phiên bản này hiện đã hết hàng.', 'error');
      return;
    }

    if (soLuong > soLuongTon) {
      toast('Số lượng vượt quá tồn kho.', 'error');
      return;
    }

    return run(() => add(sp, phienBanChon, soLuong), 'Đã thêm vào giỏ hàng');
  };

  const muaNgay = () => {
    if (!phienBanChon) {
      toast('Vui lòng chọn phiên bản sản phẩm.', 'error');
      return;
    }

    if (!conHang) {
      toast('Phiên bản này hiện không có sẵn.', 'error');
      return;
    }

    if (soLuong > soLuongTon) {
      toast('Số lượng vượt quá tồn kho.', 'error');
      return;
    }

    if (!isCustomer) {
      navigate('/login');
      return;
    }

    navigate('/checkout', {
      state: {
        mua: phienBanChon.maPhienBan,
        sl: soLuong,
        maSanPham: phienBanChon.maPhienBan,
        soLuong,
      },
    });
  };

  const themYeuThich = () =>
      run(() => wishlistService.add(sp.maSanPham), 'Đã thêm vào yêu thích');

  const themSoSanh = () =>
      run(
          () => toggle(sp.maSanPham),
          ids.includes(sp.maSanPham) ? 'Đã bỏ khỏi so sánh' : 'Đã thêm vào so sánh'
      );

  const thongSo = sp.thongSo || {};
  const danhSachAnh = sp.hinhAnhs || [];

  return (
      <>
        <nav className="crumbs" aria-label="Đường dẫn">
          <Link to="/">Trang chủ</Link>
          <span>›</span>
          <Link to={`/?loai=${sp.maLoai}`}>{sp.tenLoai}</Link>
          <span>›</span>
          <b>{sp.tenMay}</b>
        </nav>

        <div className="detail">
          <div>
            <ProductImage
                className="detail-img"
                src={image || sp.anhChinh}
                alt={sp.tenMay}
            />

            <div className="thumbs">
              {danhSachAnh.map((anh) => (
                  <ProductImage
                      key={anh.maHinhAnh || anh.duongDan}
                      src={anh.duongDan}
                      alt=""
                      onClick={() => setImage(anh.duongDan)}
                  />
              ))}
            </div>

            <section className="desc">
              <h3>Mô tả sản phẩm</h3>
              {(sp.moTa || 'Đang cập nhật mô tả cho sản phẩm này.')
                  .split(/\n\s*\n/)
                  .map((text, index) => <p key={index}>{text}</p>)}
            </section>

            <section className="commit">
              <h3>PhoneNest cam kết</h3>
              <ul>
                {COMMITS.map((c) => (
                    <li key={c.title}>
                      <Icon name={c.icon} size={22} />
                      <span>
                    <b>{c.title}</b>
                        {c.text}
                  </span>
                    </li>
                ))}
              </ul>
            </section>
          </div>

          <div>
            <h1>{sp.tenMay}</h1>
            <p className="muted">Hãng: {sp.tenThuongHieu || sp.tenLoai}</p>
            <div className="price big">{formatPrice(giaBan)}</div>

            <section style={{ margin: '20px 0' }}>
              <h3 style={{ marginBottom: 10 }}>Dung lượng RAM và bộ nhớ</h3>

              {cauHinhs.length > 0 ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                    {cauHinhs.map((c) => {
                      const dangChon =
                          phienBanChon &&
                          Number(phienBanChon.ram) === Number(c.ram) &&
                          Number(phienBanChon.boNho) === Number(c.boNho);

                      return (
                          <button
                              key={`${c.ram}-${c.boNho}`}
                              type="button"
                              className={dangChon ? 'btn btn-primary' : 'btn'}
                              onClick={() => chonCauHinh(c)}
                              aria-pressed={Boolean(dangChon)}
                          >
                            RAM {c.ram}GB / {c.boNho}GB
                          </button>
                      );
                    })}
                  </div>
              ) : (
                  <p className="muted">Sản phẩm chưa có phiên bản.</p>
              )}
            </section>

            {phienBanChon && (
                <section style={{ margin: '20px 0' }}>
                  <h3 style={{ marginBottom: 10 }}>Màu sắc</h3>

                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                    {mauSacTheoCauHinh.map((pb) => {
                      const dangChon = String(pb.maPhienBan) === String(maPhienBanChon);
                      const mauConHang = isVariantAvailable(pb);

                      return (
                          <button
                              key={pb.maPhienBan}
                              type="button"
                              className={dangChon ? 'btn btn-primary' : 'btn'}
                              onClick={() => chonMauSac(pb.maPhienBan)}
                              aria-pressed={dangChon}
                              style={{
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                gap: 4,
                                minWidth: 90,
                                opacity: mauConHang ? 1 : 0.65,
                              }}
                          >
                            <span>{pb.mauSac || 'Mặc định'}</span>
                            {!mauConHang && (
                                <small>
                                  {isVariantDiscontinued(pb) ? 'Ngừng bán' : 'Hết hàng'}
                                </small>
                            )}
                          </button>
                      );
                    })}
                  </div>
                </section>
            )}

            <div style={{ margin: '16px 0' }}>
              <StatusBadge status={productStatus(sanPhamHienThi)} />

              {!phienBanChon && (
                  <p className="muted">Sản phẩm chưa có phiên bản để bán.</p>
              )}
              {ngungKinhDoanh && (
                  <p className="muted">Phiên bản này đã ngừng kinh doanh.</p>
              )}
              {hetHang && (
                  <p className="muted">Phiên bản này hiện đã hết hàng.</p>
              )}
              {conHang && soLuongTon <= 10 && (
                  <p className="low-stock">Chỉ còn {soLuongTon} máy</p>
              )}
              {conHang && soLuongTon > 10 && (
                  <p className="muted">Còn {soLuongTon} sản phẩm trong kho</p>
              )}
            </div>

            <div className="buy-box" ref={boxRef}>
              <div className="buy-main">
                <input
                    type="number"
                    min="1"
                    max={soLuongTon}
                    value={soLuong}
                    disabled={!conHang}
                    onChange={(e) => capNhatSoLuong(e.target.value)}
                    className="qty"
                    aria-label="Số lượng"
                />

                <button
                    type="button"
                    className="btn btn-primary btn-lg"
                    disabled={!conHang}
                    onClick={themVaoGio}
                >
                  Thêm vào giỏ
                </button>

                {isCustomer && (
                    <button
                        type="button"
                        className="btn btn-buy btn-lg"
                        disabled={!conHang}
                        onClick={muaNgay}
                    >
                      Mua ngay
                    </button>
                )}
              </div>

              <div className="buy-sub">
                {isCustomer && (
                    <button type="button" className="btn" onClick={themYeuThich}>
                      <Icon name="heart" size={16} />
                      Yêu thích
                    </button>
                )}

                <button type="button" className="btn" onClick={themSoSanh}>
                  <Icon name="compare" size={16} />
                  {ids.includes(sp.maSanPham) ? 'Bỏ so sánh' : 'So sánh'}
                </button>
              </div>
            </div>

            <h3>Thông số kỹ thuật</h3>
            <ul className="specs">
              {SPEC_FIELDS
                  .filter((f) => specValue(f, thongSo) !== '—')
                  .map((f) => (
                      <li key={f.key}>
                        <span>{f.label}</span>
                        <b>{specValue(f, thongSo)}</b>
                      </li>
                  ))}
            </ul>
          </div>
        </div>

        <ReviewSection ma={ma} />
        <QuestionSection ma={ma} />

        {related?.length > 0 && (
            <section>
              <h3>Sản phẩm liên quan</h3>
              <div className="grid">
                {related.map((r) => (
                    <ProductCard key={r.maSanPham} sp={r} />
                ))}
              </div>
            </section>
        )}

        <div className="sticky-spacer no-print" />

        <div
            className={showBar ? 'sticky-bar on no-print' : 'sticky-bar no-print'}
            aria-hidden={!showBar}
        >
          <div className="container sticky-inner">
            <ProductImage src={sp.anhChinh} alt="" />

            <div className="sticky-name">
              <b>{sp.tenMay}</b>
              <span className="muted">Đơn giá {formatPrice(giaBan)}</span>
            </div>

            <div className="sticky-total">
              <span className="muted">Tạm tính</span>
              <b>{formatPrice(giaBan * soLuong)}</b>
            </div>

            <div className="sticky-actions">
              <button
                  type="button"
                  className="btn"
                  disabled={!conHang}
                  tabIndex={showBar ? 0 : -1}
                  onClick={themVaoGio}
              >
                <Icon name="cart" size={18} />
                Thêm vào giỏ
              </button>

              {isCustomer && (
                  <button
                      type="button"
                      className="btn btn-primary"
                      disabled={!conHang}
                      tabIndex={showBar ? 0 : -1}
                      onClick={muaNgay}
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