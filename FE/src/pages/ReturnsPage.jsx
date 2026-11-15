import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import StatusBadge from '../components/StatusBadge';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { orderService } from '../service/orderService';
import { errorMessage, formatDate, formatDay, formatPrice } from '../utils/format';
import { ORDER_STATUS } from '../config/constants';
import { getOrderActions } from '../utils/orderActions';

const LOAI = {
  TRA_HANG_HOAN_TIEN: 'Trả hàng / đề nghị hoàn tiền',
  DOI_HANG: 'Đổi sản phẩm',
  BAO_HANH: 'Bảo hành',
  KHIEU_NAI: 'Khiếu nại',
};

const FORM_RONG = {
  maDonHang: '', maPhienBan: '', soLuong: 1, lyDo: '',
};

export default function ReturnsPage() {
  const [params] = useSearchParams();
  const queryLoai = params.get('loai');
  const [loai, setLoai] = useState(LOAI[queryLoai] ? queryLoai : 'TRA_HANG_HOAN_TIEN');
  const [form, setForm] = useState({ ...FORM_RONG, maDonHang: params.get('maDonHang') || '' });
  const [locLoai, setLocLoai] = useState('');
  const [dangGui, setDangGui] = useState(false);
  const [imageFile, setImageFile] = useState(null);
  const [imagePreview, setImagePreview] = useState('');
  const toast = useToast();

  useEffect(() => {
    if (!imageFile) {
      setImagePreview('');
      return undefined;
    }
    const url = URL.createObjectURL(imageFile);
    setImagePreview(url);
    return () => URL.revokeObjectURL(url);
  }, [imageFile]);

  const chonAnh = (event) => {
    const file = event.target.files?.[0] || null;
    event.target.value = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      toast('Chỉ hỗ trợ JPG, PNG hoặc WEBP', 'error');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      toast('Ảnh không được vượt quá 5MB', 'error');
      return;
    }
    setImageFile(file);
  };

  const { data: orders, error: orderError } = useFetch(orderService.list);
  const { data: requests, loading, error, reload } = useFetch(async () => {
    const [doiTra, hauMai] = await Promise.all([
      customerService.returns(), customerService.afterSales(),
    ]);
    return [
      ...(doiTra || []).map((r) => ({ ...r, loai: r.loaiYeuCau || 'TRA_HANG_HOAN_TIEN' })),
      ...(hauMai || []),
    ].sort((a, b) => new Date(b.ngayTao) - new Date(a.ngayTao));
  });

  const donHopLe = useMemo(() => (orders || []).filter((o) => {
    const actions = getOrderActions(o.trangThai);
    return loai === 'KHIEU_NAI' ? actions.complain : actions.afterDelivery;
  }), [orders, loai]);
  const donDangChon = donHopLe.find((o) => o.maDonHang === form.maDonHang);
  const nhanDon = (don) => {
    const dsSanPham = don.chiTiet || [];
    const ten = dsSanPham[0]?.tenMay || 'Không có tên sản phẩm';
    const them = dsSanPham.length > 1 ? ` + ${dsSanPham.length - 1} sản phẩm khác` : '';
    return `Đơn ${don.maDonHang} · ${formatDay(don.ngayMua)} · ${formatPrice(don.tongTien)} · ${ten}${them}`;
  };
  const sanPhamDangChon = donDangChon?.chiTiet?.find((d) => d.maPhienBan === form.maPhienBan);
  const canChonSanPham = loai !== 'KHIEU_NAI';
  const laDoiTra = loai === 'DOI_HANG' || loai === 'TRA_HANG_HOAN_TIEN';
  const dangLoc = (requests || []).filter((r) => !locLoai || r.loai === locLoai);

  const doiLoai = (value) => {
    setLoai(value);
    setForm({ ...FORM_RONG, maDonHang: value === 'KHIEU_NAI' ? form.maDonHang : '' });
  };

  const guiYeuCau = async (e) => {
    e.preventDefault();
    if (dangGui) return;
    if (!donDangChon || (canChonSanPham && !sanPhamDangChon)) {
      toast('Vui lòng chọn đơn hàng và sản phẩm hợp lệ', 'error');
      return;
    }
    setDangGui(true);
    try {
      const uploadedUrl = imageFile ? (await customerService.uploadAfterSaleImage(imageFile)).url : null;
      const common = {
        maDonHang: form.maDonHang,
        lyDo: form.lyDo.trim(),
        hinhAnhMinhChung: uploadedUrl,
      };
      if (laDoiTra) {
        await customerService.requestReturn({
          ...common, loaiYeuCau: loai, maPhienBan: form.maPhienBan,
          soLuong: Number(form.soLuong),
        });
      } else {
        await customerService.createAfterSale({
          ...common, loai, maPhienBan: loai === 'BAO_HANH' ? form.maPhienBan : null,
        });
      }
      toast('Đã gửi yêu cầu. Bạn có thể theo dõi tiến độ ngay bên dưới.');
      setForm({ ...FORM_RONG });
      setImageFile(null);
      reload();
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      setDangGui(false);
    }
  };

  return (
    <div className="checkout after-sales-page">
      <h2>Trung tâm hậu mãi</h2>
      <p className="muted">Gửi yêu cầu đổi trả, bảo hành, khiếu nại và theo dõi kết quả xử lý ở cùng một nơi.</p>

      <section className="card after-sales-section">
        <h3>Tạo yêu cầu mới</h3>
        <p className="muted">Đổi trả được tiếp nhận trong 7 ngày từ khi giao. Bảo hành phụ thuộc thời hạn ghi trên sản phẩm đã mua.</p>
        {orderError && <p className="error">Không tải được đơn hàng: {orderError}</p>}
        <form className="form" onSubmit={guiYeuCau}>
          <label className="field">
            <span>Loại yêu cầu</span>
            <select value={loai} onChange={(e) => doiLoai(e.target.value)}>
              {Object.entries(LOAI).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label className="field">
            <span>Đơn hàng</span>
            <select required value={donDangChon ? form.maDonHang : ''}
              onChange={(e) => setForm({ ...form, maDonHang: e.target.value, maPhienBan: '', soLuong: 1 })}>
              <option value="">Chọn đơn hàng theo sản phẩm, ngày mua và số tiền</option>
              {donHopLe.map((o) => <option key={o.maDonHang} value={o.maDonHang}>{nhanDon(o)}</option>)}
            </select>
          </label>
          {donDangChon && (
            <div className="after-sales-order-preview">
              <b>Đơn hàng {donDangChon.maDonHang}</b>
              <div className="muted">Đặt ngày {formatDay(donDangChon.ngayMua)} · {ORDER_STATUS[donDangChon.trangThai] || donDangChon.trangThai}</div>
              {(donDangChon.chiTiet || []).map((d) => (
                <div key={d.maPhienBan || d.maSanPham} className="row between">
                  <span>{d.tenMay} × {d.soLuong}</span>
                  <span>{formatPrice(Number(d.donGia) * d.soLuong)}</span>
                </div>
              ))}
              <div className="row between"><b>Tổng đơn</b><b>{formatPrice(donDangChon.tongTien)}</b></div>
              <Link to={`/orders/${donDangChon.maDonHang}`}>Xem chi tiết đơn hàng</Link>
            </div>
          )}
          {canChonSanPham && (
            <label className="field">
              <span>Sản phẩm cần {loai === 'BAO_HANH' ? 'bảo hành' : 'đổi trả'}</span>
              <select required value={form.maPhienBan}
                onChange={(e) => setForm({ ...form, maPhienBan: e.target.value, soLuong: 1 })}>
                <option value="">Chọn sản phẩm</option>
                {(donDangChon?.chiTiet || []).map((d) =>
                  <option key={d.maPhienBan} value={d.maPhienBan}>{d.tenMay} (đã mua {d.soLuong})</option>
                )}
              </select>
            </label>
          )}
          {laDoiTra && sanPhamDangChon && (
            <label className="field">
              <span>Số lượng đổi trả</span>
              <input required type="number" min="1" max={sanPhamDangChon.soLuong} value={form.soLuong}
                onChange={(e) => setForm({ ...form, soLuong: e.target.value })} />
            </label>
          )}
          <label className="field">
            <span>Mô tả vấn đề</span>
            <textarea rows="4" required maxLength="2000" value={form.lyDo} placeholder="Mô tả chi tiết tình trạng sản phẩm hoặc vấn đề gặp phải"
              onChange={(e) => setForm({ ...form, lyDo: e.target.value })} />
          </label>
          <label className="field">
            <span>Ảnh minh chứng (không bắt buộc)</span>
            <input type="file" accept="image/jpeg,image/png,image/webp" onChange={chonAnh} disabled={dangGui} />
            <small className="muted">Tải ảnh từ máy: JPG, PNG hoặc WEBP, tối đa 5MB.</small>
          </label>
          {imagePreview && (
            <div className="after-sales-image-preview">
              <img src={imagePreview} alt="Ảnh minh chứng đã chọn" />
              <div>
                <span className="muted">{imageFile?.name}</span>
                <button className="btn" type="button" disabled={dangGui} onClick={() => setImageFile(null)}>Bỏ ảnh</button>
              </div>
            </div>
          )}
          <button type="submit" className="btn btn-primary" disabled={dangGui || !donDangChon || (canChonSanPham && !sanPhamDangChon)}>
            {dangGui ? 'Đang gửi...' : 'Gửi yêu cầu'}
          </button>
        </form>
      </section>

      <section className="after-sales-section">
        <div className="row between after-sales-heading">
          <h3>Yêu cầu của tôi</h3>
          <select aria-label="Lọc loại yêu cầu" value={locLoai} onChange={(e) => setLocLoai(e.target.value)}>
            <option value="">Tất cả loại yêu cầu</option>
            {Object.entries(LOAI).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </div>
        {loading && <p className="muted">Đang tải yêu cầu...</p>}
        {error && <div className="error">Không tải được yêu cầu: {error} <button className="btn" onClick={reload}>Thử lại</button></div>}
        {!loading && !error && dangLoc.length === 0 && <p className="muted">Chưa có yêu cầu phù hợp.</p>}
        {dangLoc.map((r) => (
          <article key={r.maYeuCau} className="card after-sales-request">
            <div className="row between">
              <div><b>{LOAI[r.loai] || r.loai}</b> · <span className="muted">{r.maYeuCau}</span></div>
              <StatusBadge status={r.trangThai} />
            </div>
            <p>Đơn hàng: <Link to={`/orders/${r.maDonHang}`}>{r.maDonHang}</Link></p>
            {r.tenSanPham && <p>Sản phẩm: {r.tenSanPham}{r.soLuong ? ` · SL: ${r.soLuong}` : ''}</p>}
            <p>{r.lyDo}</p>
            {r.hinhAnhMinhChung && <a href={r.hinhAnhMinhChung} rel="noreferrer" target="_blank">Xem ảnh minh chứng</a>}
            <p className="muted">Gửi lúc: {formatDate(r.ngayTao)}{r.ngayCapNhat && ` · Cập nhật: ${formatDate(r.ngayCapNhat)}`}</p>
            {r.ghiChuXuLy && <p><b>Phản hồi:</b> {r.ghiChuXuLy}</p>}
            {r.lichSu?.length > 0 && (
              <details>
                <summary>Xem lịch sử xử lý ({r.lichSu.length})</summary>
                <ol className="after-sales-timeline">
                  {r.lichSu.map((item, index) => (
                    <li key={`${r.maYeuCau}-${index}`}>
                      <StatusBadge status={item.trangThai} /> <span className="muted">{formatDate(item.thoiGian)}</span>
                      {item.ghiChu && <div>{item.ghiChu}</div>}
                    </li>
                  ))}
                </ol>
              </details>
            )}
          </article>
        ))}
      </section>
    </div>
  );
}
