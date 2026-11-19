import { useEffect } from 'react';

// Các khối hiện dần khi cuộn tới. Chỉ thêm hiệu ứng, không thay đổi nội dung hay thao tác.
const SELECTOR = [
  '.block-head',
  '.product-card',
  '.services',
  '.brand-tiles',
  '.top-sellers',
  '.desc',
  '.commit',
  '.review',
  '.card.order',
  '.empty-state',
].join(',');

export default function useScrollReveal(rootRef) {
  useEffect(() => {
    const root = rootRef.current;
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (!root || reduce || !('IntersectionObserver' in window) || !('MutationObserver' in window)) return undefined;

    document.documentElement.classList.add('reveal-ready');
    const io = new IntersectionObserver((entries) => {
      let n = 0;
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        // Các phần tử cùng xuất hiện một lúc hiện lần lượt, cách nhau 60ms (tối đa 6 bước).
        entry.target.style.setProperty('--reveal-delay', `${Math.min(n, 6) * 60}ms`);
        n += 1;
        entry.target.classList.add('is-in');
        io.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -6% 0px', threshold: 0.06 });

    // Theo dõi theo từng lần chạy effect (StrictMode chạy effect 2 lần ở môi trường dev).
    const observed = new WeakSet();
    const scan = () => {
      root.querySelectorAll(SELECTOR).forEach((el) => {
        if (observed.has(el) || el.classList.contains('is-in')) return;
        observed.add(el);
        el.dataset.reveal = '1';
        io.observe(el);
      });
    };
    scan();
    const mo = new MutationObserver(scan);
    mo.observe(root, { childList: true, subtree: true });

    return () => {
      io.disconnect();
      mo.disconnect();
      document.documentElement.classList.remove('reveal-ready');
    };
  }, [rootRef]);
}
