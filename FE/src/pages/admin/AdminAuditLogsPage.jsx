import { useEffect, useState } from 'react';
import Modal from '../../components/Modal';
import useDebounce from '../../hooks/useDebounce';
import { adminService } from '../../service/adminService';
import { errorMessage } from '../../utils/format';

export const ACTION_LABELS = {
  CREATE_PRODUCT: 'Tạo sản phẩm',
  UPDATE_PRODUCT: 'Cập nhật sản phẩm',
  DELETE_PRODUCT: 'Xóa sản phẩm',

  CREATE_CATEGORY: 'Tạo danh mục',
  UPDATE_CATEGORY: 'Cập nhật danh mục',
  DELETE_CATEGORY: 'Xóa danh mục',

  STOCK_IMPORT: 'Nhập kho',
  STOCK_ADJUSTMENT: 'Điều chỉnh tồn kho',

  CHANGE_ORDER_STATUS: 'Đổi trạng thái đơn hàng',
  UPDATE_ORDER_ITEM: 'Cập nhật sản phẩm trong đơn',

  LOCK_ACCOUNT: 'Khóa tài khoản',
  UNLOCK_ACCOUNT: 'Mở khóa tài khoản',
  UPDATE_ACCOUNT: 'Cập nhật tài khoản',
  DELETE_ACCOUNT: 'Xóa tài khoản',

  CREATE_PROMOTION: 'Tạo khuyến mãi',
  UPDATE_PROMOTION: 'Cập nhật khuyến mãi',
  DELETE_PROMOTION: 'Xóa khuyến mãi',

  CREATE_SHIPPING_FEE: 'Tạo phí vận chuyển',
  UPDATE_SHIPPING_FEE: 'Cập nhật phí vận chuyển',
  DELETE_SHIPPING_FEE: 'Xóa phí vận chuyển',

  ANSWER_QUESTION: 'Trả lời câu hỏi',
  DELETE_QUESTION: 'Xóa câu hỏi',

  DELETE_REVIEW: 'Xóa đánh giá',

  CHANGE_RETURN_STATUS: 'Đổi trạng thái đổi trả',
};

export const ENTITY_LABELS = {
  PRODUCT: 'Sản phẩm',
  CATEGORY: 'Danh mục',
  INVENTORY: 'Kho hàng',
  ORDER: 'Đơn hàng',
  ACCOUNT: 'Tài khoản',
  PROMOTION: 'Khuyến mãi',
  SHIPPING_FEE: 'Phí vận chuyển',
  QUESTION: 'Câu hỏi',
  REVIEW: 'Đánh giá',
  RETURN: 'Đổi trả',
};

const MONEY_FIELDS = new Set([
  'giaBan',
  'giaNiemYet',
  'phiCoBan',
  'donToiThieu',
  'soTienGiamToiDa',
  'tongTien',
  'tamTinh',
  'giamGia',
  'tienGiam',
]);

export function formatAuditDateTime(d) {
  if (!d) return '—';
  const dt = new Date(d);
  if (isNaN(dt.getTime())) return String(d);
  const pad = (n) => String(n).padStart(2, '0');
  const day = pad(dt.getDate());
  const month = pad(dt.getMonth() + 1);
  const year = dt.getFullYear();
  const hours = pad(dt.getHours());
  const mins = pad(dt.getMinutes());
  const secs = pad(dt.getSeconds());
  return `${day}/${month}/${year} ${hours}:${mins}:${secs}`;
}

export function getActionBadgeClass(action) {
  if (!action) return 'badge';
  if (action.startsWith('CREATE') || action === 'UNLOCK_ACCOUNT') return 'badge badge-audit-create';
  if (action.startsWith('DELETE') || action === 'LOCK_ACCOUNT') return 'badge badge-audit-delete';
  if (action.startsWith('STOCK')) return 'badge badge-audit-stock';
  return 'badge badge-audit-update';
}

function formatFieldValue(val, key = '') {
  if (val === null || val === undefined) return '—';
  if (typeof val === 'boolean') return val ? 'Có' : 'Không';
  if (typeof val === 'number') {
    if (MONEY_FIELDS.has(key)) {
      return `${val.toLocaleString('vi-VN')} ₫`;
    }
    return val.toLocaleString('vi-VN');
  }
  if (typeof val === 'string') {
    if (!val.trim()) return '—';
    if (MONEY_FIELDS.has(key) && !isNaN(Number(val))) {
      return `${Number(val).toLocaleString('vi-VN')} ₫`;
    }
    return val;
  }
  if (typeof val === 'object') {
    return <pre className="json-block">{JSON.stringify(val, null, 2)}</pre>;
  }
  return String(val);
}

function AuditVisualDiff({ before, after }) {
  const isCreate = before === null && after !== null;
  const isDelete = before !== null && after === null;
  const isBothNull = before === null && after === null;

  if (isBothNull) {
    return <p className="muted">Không có dữ liệu thay đổi ghi nhận.</p>;
  }

  // Handle case where payload is a raw string (malformed legacy JSON or plain text)
  if (typeof before === 'string' && typeof after === 'string') {
    return (
      <div className="row" style={{ alignItems: 'flex-start', gap: '16px' }}>
        <div style={{ flex: 1 }}>
          <b>Trước:</b>
          <pre className="json-block">{before}</pre>
        </div>
        <div style={{ flex: 1 }}>
          <b>Sau:</b>
          <pre className="json-block">{after}</pre>
        </div>
      </div>
    );
  }

  if (isCreate) {
    if (typeof after === 'object' && after !== null && !Array.isArray(after)) {
      const keys = Object.keys(after);
      if (keys.length === 0) return <p className="muted">Dữ liệu rỗng.</p>;
      return (
        <div>
          <p className="muted" style={{ margin: '0 0 8px' }}>Dữ liệu được tạo:</p>
          <table className="audit-diff-table">
            <thead>
              <tr>
                <th style={{ width: '30%' }}>Trường</th>
                <th>Giá trị tạo mới</th>
              </tr>
            </thead>
            <tbody>
              {keys.map((k) => (
                <tr key={k}>
                  <td><code>{k}</code></td>
                  <td>
                    {typeof after[k] === 'object' && after[k] !== null ? (
                      formatFieldValue(after[k], k)
                    ) : (
                      <span className="audit-diff-val-new">{formatFieldValue(after[k], k)}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    }
    return (
      <div>
        <p className="muted" style={{ margin: '0 0 8px' }}>Dữ liệu được tạo:</p>
        <pre className="json-block">{typeof after === 'string' ? after : JSON.stringify(after, null, 2)}</pre>
      </div>
    );
  }

  if (isDelete) {
    if (typeof before === 'object' && before !== null && !Array.isArray(before)) {
      const keys = Object.keys(before);
      if (keys.length === 0) return <p className="muted">Dữ liệu rỗng.</p>;
      return (
        <div>
          <p className="muted" style={{ margin: '0 0 8px' }}>Dữ liệu trước khi xóa:</p>
          <table className="audit-diff-table">
            <thead>
              <tr>
                <th style={{ width: '30%' }}>Trường</th>
                <th>Giá trị trước khi xóa</th>
              </tr>
            </thead>
            <tbody>
              {keys.map((k) => (
                <tr key={k}>
                  <td><code>{k}</code></td>
                  <td>
                    {typeof before[k] === 'object' && before[k] !== null ? (
                      formatFieldValue(before[k], k)
                    ) : (
                      <span className="audit-diff-val-old">{formatFieldValue(before[k], k)}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    }
    return (
      <div>
        <p className="muted" style={{ margin: '0 0 8px' }}>Dữ liệu trước khi xóa:</p>
        <pre className="json-block">{typeof before === 'string' ? before : JSON.stringify(before, null, 2)}</pre>
      </div>
    );
  }

  // UPDATE / CHANGE comparison
  const objBefore = (typeof before === 'object' && before !== null && !Array.isArray(before)) ? before : {};
  const objAfter = (typeof after === 'object' && after !== null && !Array.isArray(after)) ? after : {};
  const allKeys = Array.from(new Set([...Object.keys(objBefore), ...Object.keys(objAfter)]));

  if (allKeys.length === 0) {
    return (
      <div className="row" style={{ alignItems: 'flex-start', gap: '16px' }}>
        <div style={{ flex: 1 }}>
          <b>Trước:</b>
          <pre className="json-block">{JSON.stringify(before, null, 2)}</pre>
        </div>
        <div style={{ flex: 1 }}>
          <b>Sau:</b>
          <pre className="json-block">{JSON.stringify(after, null, 2)}</pre>
        </div>
      </div>
    );
  }

  return (
    <div>
      <p className="muted" style={{ margin: '0 0 8px' }}>So sánh thay đổi (Trước → Sau):</p>
      <table className="audit-diff-table">
        <thead>
          <tr>
            <th style={{ width: '25%' }}>Trường</th>
            <th style={{ width: '37.5%' }}>Trước</th>
            <th style={{ width: '37.5%' }}>Sau</th>
          </tr>
        </thead>
        <tbody>
          {allKeys.map((k) => {
            const vBefore = objBefore[k];
            const vAfter = objAfter[k];
            const isDifferent = JSON.stringify(vBefore) !== JSON.stringify(vAfter);
            const isNested = (typeof vBefore === 'object' && vBefore !== null) || (typeof vAfter === 'object' && vAfter !== null);

            return (
              <tr key={k} className={isDifferent ? 'changed' : ''}>
                <td><code>{k}</code></td>
                <td>
                  {isNested ? (
                    formatFieldValue(vBefore, k)
                  ) : (
                    <span className={isDifferent ? 'audit-diff-val-old' : ''}>{formatFieldValue(vBefore, k)}</span>
                  )}
                </td>
                <td>
                  {isNested ? (
                    formatFieldValue(vAfter, k)
                  ) : (
                    <span className={isDifferent ? 'audit-diff-val-new' : ''}>{formatFieldValue(vAfter, k)}</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function AuditDetailModal({ auditId, onClose }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    adminService.auditLogDetail(auditId)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(errorMessage(err));
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [auditId]);

  return (
    <Modal title={`Chi tiết nhật ký hoạt động #${auditId}`} onClose={onClose} wide>
      {loading && <p className="center muted">Đang tải thông tin chi tiết...</p>}
      {error && <p className="center error">{error}</p>}
      {!loading && !error && data && (
        <>
          <section className="audit-detail-section">
            <h4>Thông tin chung</h4>
            <dl className="audit-detail-grid">
              <div>
                <dt>Mã nhật ký</dt>
                <dd>#{data.maNhatKy}</dd>
              </div>
              <div>
                <dt>Thời gian</dt>
                <dd>{formatAuditDateTime(data.thoiGian)}</dd>
              </div>
              <div>
                <dt>Người thực hiện</dt>
                <dd>
                  <b>{data.tenNguoiThucHien || '—'}</b>
                  {data.maTk && <span className="muted"> ({data.maTk})</span>}
                </dd>
              </div>
              <div>
                <dt>Hành động</dt>
                <dd>
                  <span className={getActionBadgeClass(data.hanhDong)}>
                    {ACTION_LABELS[data.hanhDong] || data.hanhDong}
                  </span>
                  <small className="muted" style={{ display: 'block', marginTop: '2px' }}><code>{data.hanhDong}</code></small>
                </dd>
              </div>
              <div>
                <dt>Đối tượng</dt>
                <dd>
                  <b>{ENTITY_LABELS[data.loaiDoiTuong] || data.loaiDoiTuong}</b>
                  <small className="muted" style={{ display: 'block', marginTop: '2px' }}><code>{data.loaiDoiTuong}</code></small>
                </dd>
              </div>
              <div>
                <dt>Mã đối tượng</dt>
                <dd><code>{data.maDoiTuong || '—'}</code></dd>
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <dt>Mô tả</dt>
                <dd>{data.moTa || '—'}</dd>
              </div>
            </dl>
          </section>

          <section className="audit-detail-section">
            <h4>Thông tin request</h4>
            <dl className="audit-detail-grid">
              <div>
                <dt>Địa chỉ IP</dt>
                <dd><code>{data.ipAddress || '—'}</code></dd>
              </div>
              <div>
                <dt>Phương thức HTTP</dt>
                <dd><code>{data.requestMethod || '—'}</code></dd>
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <dt>Đường dẫn request</dt>
                <dd><code>{data.requestPath || '—'}</code></dd>
              </div>
            </dl>
          </section>

          <section className="audit-detail-section">
            <h4>Thay đổi dữ liệu</h4>
            <AuditVisualDiff before={data.duLieuTruoc} after={data.duLieuSau} />
          </section>
        </>
      )}
    </Modal>
  );
}

export default function AdminAuditLogsPage() {
  const [keywordInput, setKeywordInput] = useState('');
  const [actorInput, setActorInput] = useState('');
  const debouncedKeyword = useDebounce(keywordInput, 400);
  const debouncedActor = useDebounce(actorInput, 400);

  const [action, setAction] = useState('');
  const [entity, setEntity] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const [sort, setSort] = useState('thoiGian');
  const [direction, setDirection] = useState('desc');

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [selectedAuditId, setSelectedAuditId] = useState(null);

  // Validate date range: fromDate must not be after toDate
  const isDateRangeInvalid = Boolean(fromDate && toDate && fromDate > toDate);

  // Any filter change must reset page to 0
  const handleKeywordChange = (e) => {
    setKeywordInput(e.target.value);
    setPage(0);
  };

  const handleActorChange = (e) => {
    setActorInput(e.target.value);
    setPage(0);
  };

  const handleActionChange = (e) => {
    setAction(e.target.value);
    setPage(0);
  };

  const handleEntityChange = (e) => {
    setEntity(e.target.value);
    setPage(0);
  };

  const handleFromDateChange = (e) => {
    setFromDate(e.target.value);
    setPage(0);
  };

  const handleToDateChange = (e) => {
    setToDate(e.target.value);
    setPage(0);
  };

  const handleSizeChange = (e) => {
    setSize(Number(e.target.value));
    setPage(0);
  };

  const handleReset = () => {
    setKeywordInput('');
    setActorInput('');
    setAction('');
    setEntity('');
    setFromDate('');
    setToDate('');
    setSort('thoiGian');
    setDirection('desc');
    setPage(0);
  };

  const handleSortToggle = (colKey) => {
    if (sort === colKey) {
      setDirection((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSort(colKey);
      setDirection('desc');
    }
  };

  const renderSortIndicator = (colKey) => {
    if (sort !== colKey) return <span className="muted" style={{ opacity: 0.4 }}> ⇅</span>;
    return direction === 'asc' ? ' ▲' : ' ▼';
  };

  // Fetch list with race cancellation safety
  useEffect(() => {
    if (isDateRangeInvalid) return;

    let active = true;
    setLoading(true);
    setError('');

    const params = {
      page,
      size,
      sort,
      direction,
    };
    if (debouncedKeyword.trim()) params.keyword = debouncedKeyword.trim();
    if (debouncedActor.trim()) params.actor = debouncedActor.trim();
    if (action) params.action = action;
    if (entity) params.entity = entity;
    if (fromDate) params.from = `${fromDate}T00:00:00`;
    if (toDate) params.to = `${toDate}T23:59:59`;

    adminService.auditLogs(params)
      .then((res) => {
        if (active) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(errorMessage(err));
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [page, size, sort, direction, debouncedKeyword, debouncedActor, action, entity, fromDate, toDate, isDateRangeInvalid]);

  const items = data?.noiDung || [];
  const totalCount = data?.tongSo || 0;
  const totalPages = data?.tongTrang || 0;
  const isFiltered = Boolean(debouncedKeyword || debouncedActor || action || entity || fromDate || toDate);

  return (
    <>
      <div className="audit-header">
        <h2>Nhật ký hoạt động</h2>
        <p>Theo dõi các thay đổi quan trọng được thực hiện bởi quản trị viên.</p>
      </div>

      <div className="audit-filters">
        <div className="audit-filter-row">
          <input
            className="audit-filter-input"
            placeholder="Tìm kiếm nhật ký..."
            value={keywordInput}
            onChange={handleKeywordChange}
          />
          <input
            className="audit-filter-input"
            placeholder="Tên hoặc mã tài khoản..."
            value={actorInput}
            onChange={handleActorChange}
          />
          <select
            className="audit-filter-select"
            value={action}
            onChange={handleActionChange}
          >
            <option value="">Tất cả hành động</option>
            {Object.entries(ACTION_LABELS).map(([k, label]) => (
              <option key={k} value={k}>{label}</option>
            ))}
          </select>
          <select
            className="audit-filter-select"
            value={entity}
            onChange={handleEntityChange}
          >
            <option value="">Tất cả đối tượng</option>
            {Object.entries(ENTITY_LABELS).map(([k, label]) => (
              <option key={k} value={k}>{label}</option>
            ))}
          </select>
        </div>

        <div className="audit-filter-row between">
          <div className="audit-date-group">
            <span className="muted">Từ ngày:</span>
            <input
              type="date"
              value={fromDate}
              onChange={handleFromDateChange}
            />
            <span className="muted">Đến ngày:</span>
            <input
              type="date"
              value={toDate}
              onChange={handleToDateChange}
            />
          </div>
          <button className="btn" type="button" onClick={handleReset}>
            Đặt lại
          </button>
        </div>

        {isDateRangeInvalid && (
          <p className="error" style={{ margin: 0 }}>
            Ngày bắt đầu không được sau ngày kết thúc.
          </p>
        )}
      </div>

      {error && (
        <div className="card" style={{ marginBottom: '16px', background: '#fef3f2', borderColor: '#fecdca' }}>
          <p className="error" style={{ margin: 0 }}>{error}</p>
        </div>
      )}

      <div className="audit-table-wrapper">
        <table className="audit-table">
          <thead>
            <tr>
              <th className="sortable" onClick={() => handleSortToggle('thoiGian')}>
                Thời gian{renderSortIndicator('thoiGian')}
              </th>
              <th className="sortable" onClick={() => handleSortToggle('tenNguoiThucHien')}>
                Người thực hiện{renderSortIndicator('tenNguoiThucHien')}
              </th>
              <th className="sortable" onClick={() => handleSortToggle('hanhDong')}>
                Hành động{renderSortIndicator('hanhDong')}
              </th>
              <th className="sortable" onClick={() => handleSortToggle('loaiDoiTuong')}>
                Đối tượng{renderSortIndicator('loaiDoiTuong')}
              </th>
              <th className="sortable" onClick={() => handleSortToggle('maDoiTuong')}>
                Mã đối tượng{renderSortIndicator('maDoiTuong')}
              </th>
              <th>Mô tả</th>
              <th>IP</th>
              <th style={{ textAlign: 'right' }}>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8} className="center muted" style={{ padding: '48px 0' }}>
                  Đang tải nhật ký hoạt động...
                </td>
              </tr>
            ) : items.length === 0 ? (
              <tr>
                <td colSpan={8} className="center muted" style={{ padding: '48px 0' }}>
                  {isFiltered ? 'Không tìm thấy nhật ký hoạt động phù hợp.' : 'Chưa có nhật ký hoạt động.'}
                </td>
              </tr>
            ) : (
              items.map((row) => (
                <tr key={row.maNhatKy}>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {formatAuditDateTime(row.thoiGian)}
                  </td>
                  <td>
                    <div><b>{row.tenNguoiThucHien || '—'}</b></div>
                    {row.maTk && <small className="muted">{row.maTk}</small>}
                  </td>
                  <td>
                    <span className={getActionBadgeClass(row.hanhDong)}>
                      {ACTION_LABELS[row.hanhDong] || row.hanhDong}
                    </span>
                  </td>
                  <td>
                    <b>{ENTITY_LABELS[row.loaiDoiTuong] || row.loaiDoiTuong}</b>
                  </td>
                  <td>
                    <code>{row.maDoiTuong || '—'}</code>
                  </td>
                  <td style={{ maxWidth: '280px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={row.moTa}>
                    {row.moTa || '—'}
                  </td>
                  <td>
                    <small>{row.ipAddress || '—'}</small>
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      className="btn"
                      type="button"
                      onClick={() => setSelectedAuditId(row.maNhatKy)}
                    >
                      Xem chi tiết
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="audit-pagination">
        <div className="muted">
          {totalCount > 0 ? (
            `Hiển thị ${page * size + 1}–${Math.min((page + 1) * size, totalCount)} trong ${totalCount} nhật ký`
          ) : (
            'Hiển thị 0 trong 0 nhật ký'
          )}
        </div>
        <div className="row" style={{ gap: '14px' }}>
          <div className="row" style={{ gap: '6px' }}>
            <span className="muted" style={{ fontSize: '0.88rem' }}>Số dòng:</span>
            <select
              value={size}
              onChange={handleSizeChange}
              style={{ padding: '4px 8px', fontSize: '0.88rem' }}
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </div>
          <div className="row" style={{ gap: '6px' }}>
            <button
              className="btn"
              type="button"
              disabled={page === 0 || loading}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Trước
            </button>
            <span className="muted" style={{ padding: '0 8px', fontSize: '0.88rem' }}>
              Trang {page + 1} / {Math.max(1, totalPages)}
            </span>
            <button
              className="btn"
              type="button"
              disabled={page >= totalPages - 1 || totalCount === 0 || loading}
              onClick={() => setPage((p) => p + 1)}
            >
              Sau →
            </button>
          </div>
        </div>
      </div>

      {selectedAuditId !== null && (
        <AuditDetailModal
          auditId={selectedAuditId}
          onClose={() => setSelectedAuditId(null)}
        />
      )}
    </>
  );
}
