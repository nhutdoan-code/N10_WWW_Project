import { Link } from 'react-router-dom';
import { LOGO_LETTERS, LOGO_VIEWBOX } from '../assets/logoPaths';

// Logo chữ PhoneNest "Xây tổ": 9 chữ cái có độ đậm tăng dần từ nét mảnh đến nét đậm,
// như những sợi rơm dần thành chiếc tổ. "phone" màu chữ chính, "nest" màu thương hiệu.
// Không dùng hình, chữ đã được chuyển thành đường nét SVG.
function Mark({ intro }) {
  return (
    <svg className={`wordmark-svg ${intro ? 'intro' : ''}`} viewBox={LOGO_VIEWBOX} aria-hidden="true" focusable="false">
      {LOGO_LETTERS.map((d, i) => (
        <path key={i} d={d} className={i < 5 ? 'wm-phone' : 'wm-nest'} style={{ '--i': i }} />
      ))}
    </svg>
  );
}

export default function Logo({ to = '/', className = '', intro = false }) {
  if (to == null) {
    return (
      <span className={`wordmark ${className}`} role="img" aria-label="PhoneNest">
        <Mark intro={intro} />
      </span>
    );
  }
  return (
    <Link to={to} className={`wordmark ${className}`} aria-label="PhoneNest - Trang chủ">
      <Mark intro={intro} />
    </Link>
  );
}
