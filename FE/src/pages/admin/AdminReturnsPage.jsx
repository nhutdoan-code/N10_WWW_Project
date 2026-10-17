import StatusBadge from '../../components/StatusBadge';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatDay } from '../../utils/format';

export default function AdminReturnsPage() {
  const { data: list, reload } = useFetch(adminService.returns);
  const toast = useToast();

  const update = async (ma, trangThai) => {
    try {
      await adminService.updateReturn(ma, trangThai);
      toast('Đã cập nhật yêu cầu');
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  return (
    <>
      <h2>Xử lý đổi trả & hoàn tiền</h2>
      <table className="table">
        <thead>
          <tr><th>Mã</th><th>Đơn hàng</th><th>Khách</th><th>Lý do</th><th>Minh chứng</th><th>Ngày</th><th>Trạng thái</th><th></th></tr>
        </thead>
        <tbody>
          {list?.map((r) => (
            <tr key={r.maYeuCau}>
              <td>{r.maYeuCau}</td>
              <td>{r.maDonHang}</td>
              <td>{r.tenKhach}</td>
              <td>{r.lyDo}</td>
              <td>{r.hinhAnhMinhChung ? <a href={r.hinhAnhMinhChung} target="_blank" rel="noreferrer">Xem ảnh</a> : '—'}</td>
              <td>{formatDay(r.ngayTao)}</td>
              <td><StatusBadge status={r.trangThai} /></td>
              <td className="row">
                {r.trangThai === 'CHO_DUYET' && (
                  <>
                    <button className="btn" onClick={() => update(r.maYeuCau, 'DA_DUYET')}>Duyệt</button>
                    <button className="btn btn-danger" onClick={() => update(r.maYeuCau, 'TU_CHOI')}>Từ chối</button>
                  </>
                )}
                {r.trangThai === 'DA_DUYET' && <button className="btn btn-primary" onClick={() => update(r.maYeuCau, 'DA_HOAN_TIEN')}>Hoàn tiền</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
