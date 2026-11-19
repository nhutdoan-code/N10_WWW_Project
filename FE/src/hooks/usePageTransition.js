import { useEffect, useRef } from 'react';

// Hiệu ứng mờ dần khi chuyển trang. Dùng Web Animations API nên không phải gắn lại (remount) nội dung trang.
export default function usePageTransition(ref, key) {
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    const el = ref.current;
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (!el || reduce || !el.animate) return;
    el.animate(
      [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'none' }],
      { duration: 280, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)' }
    );
  }, [ref, key]);
}
