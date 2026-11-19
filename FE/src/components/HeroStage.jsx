import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { HERO_INTERVAL, HERO_VIDEOS } from '../config/heroMedia';
import { formatDungLuong, formatGiaCongBo } from '../utils/format';
import ProductImage from './ProductImage';

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// Dòng thông số ngắn lấy từ dữ liệu thật của sản phẩm (không viết lời quảng cáo chung chung).
const specLine = (p) => {
  const t = p?.thongSo || {};
  return [
    t.ram && t.boNho && `${t.ram} GB RAM, ${formatDungLuong(t.boNho)}`,
    t.kichThuocManHinh && `màn hình ${t.kichThuocManHinh}"`,
    t.ho5G && '5G',
  ].filter(Boolean).join(', ');
};

function SlideMedia({ slide, active, reduce }) {
  const ref = useRef(null);
  const media = slide.product ? HERO_VIDEOS[slide.product.maSanPham] : null;
  const [videoFailed, setVideoFailed] = useState(false);

  useEffect(() => {
    const v = ref.current;
    if (!v) return;
    if (active && !reduce) {
      v.currentTime = 0;
      v.play().catch(() => {});
    } else {
      v.pause();
    }
  }, [active, reduce]);

  if (!slide.product) return null;
  if (media && !videoFailed) {
    return (
      <video
        ref={ref}
        className="stage-video"
        src={media.src}
        poster={media.poster}
        muted
        playsInline
        loop
        preload={active ? 'auto' : 'metadata'}
        aria-hidden="true"
        onError={() => setVideoFailed(true)}
      />
    );
  }
  return (
    <div className="stage-still" aria-hidden="true">
      <ProductImage src={slide.product.anhChinh} alt="" />
    </div>
  );
}

export default function HeroStage({ slides }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [visible, setVisible] = useState(true);
  const rootRef = useRef(null);
  const reduce = prefersReducedMotion();
  const count = slides.length;
  const go = (i) => setIndex(((i % count) + count) % count);

  // Dừng tự chuyển khi banner ra khỏi màn hình hoặc tab bị ẩn để tiết kiệm pin/băng thông.
  useEffect(() => {
    const el = rootRef.current;
    if (!el || !('IntersectionObserver' in window)) return undefined;
    const io = new IntersectionObserver(([entry]) => setVisible(entry.isIntersecting), { threshold: 0.25 });
    io.observe(el);
    const onVis = () => setVisible(!document.hidden);
    document.addEventListener('visibilitychange', onVis);
    return () => {
      io.disconnect();
      document.removeEventListener('visibilitychange', onVis);
    };
  }, []);

  const running = !paused && visible && !reduce && count > 1;
  useEffect(() => {
    if (!running) return undefined;
    const t = setTimeout(() => go(index + 1), HERO_INTERVAL);
    return () => clearTimeout(t);
  }, [index, running]); // eslint-disable-line react-hooks/exhaustive-deps

  if (count === 0) return null;

  return (
    <section
      ref={rootRef}
      className={`stage ${running ? 'is-running' : ''}`}
      aria-roledescription="carousel"
      aria-label="Sản phẩm nổi bật"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      {slides.map((s, i) => {
        const active = i === index;
        const p = s.product;
        return (
          <article
            key={s.id}
            className={`stage-slide ${s.product ? 'is-product' : 'is-promo'} ${active ? 'on' : ''}`}
            aria-hidden={!active}
          >
            <SlideMedia slide={s} active={active && visible} reduce={reduce} />
            <div className="stage-copy">
              <p className="stage-kicker"><span>{s.tag}</span></p>
              <h2 className="stage-title">
                <span className="line"><span>{p ? p.tenMay : s.title}</span></span>
              </h2>
              <p className="stage-sub"><span>{p ? specLine(p) || s.text : s.text}</span></p>
            </div>
            <div className="stage-buy">
              {p ? (
                <>
                  <span>{p.giaBan != null ? <>Từ <b>{formatGiaCongBo(p.giaBan)}</b></> : 'Giá đang cập nhật'}</span>
                  <Link to={s.to} className="stage-buy-btn" tabIndex={active ? 0 : -1}>{s.cta}</Link>
                </>
              ) : (
                <>
                  <span>{s.note}</span>
                  <Link to={s.to} className="stage-buy-btn" tabIndex={active ? 0 : -1}>{s.cta}</Link>
                </>
              )}
            </div>
          </article>
        );
      })}

      {count > 1 && (
        <div className="stage-tabs" role="tablist" aria-label="Chọn banner">
          {slides.map((s, i) => (
            <button
              key={s.id}
              type="button"
              role="tab"
              aria-selected={i === index}
              className={i === index ? 'on' : ''}
              onClick={() => go(i)}
            >
              {s.tag}
              <i key={`${index}-${running}`} style={{ animationDuration: `${HERO_INTERVAL}ms` }} />
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
