import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';

export default function AccountMenu({ user, onLogout }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const initial = user.hoTen?.trim().split(' ').pop()?.[0]?.toUpperCase() || 'U';

  useEffect(() => {
    const close = (e) => !ref.current?.contains(e.target) && setOpen(false);
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  return (
    <div className="account" ref={ref}>
      <button className="account-btn" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span className="avatar">{initial}</span>
        <span className="account-name">{user.hoTen}</span>
        <Icon name="chevron" size={16} />
      </button>
      {open && (
        <div className="menu" onClick={() => setOpen(false)}>
          <div className="menu-head">
            <b>{user.hoTen}</b>
            <span className="muted">{user.email}</span>
          </div>
          <hr />
          <Link to="/account"><Icon name="user" size={17} /> Thông tin tài khoản</Link>
          <Link to="/orders"><Icon name="box" size={17} /> Đơn hàng của tôi</Link>
          <Link to="/returns"><Icon name="refresh" size={17} /> Đổi trả &amp; bảo hành</Link>
          <Link to="/account/addresses"><Icon name="pin" size={17} /> Địa chỉ giao hàng</Link>
          <hr />
          <button onClick={onLogout} className="menu-logout">Đăng xuất</button>
        </div>
      )}
    </div>
  );
}
