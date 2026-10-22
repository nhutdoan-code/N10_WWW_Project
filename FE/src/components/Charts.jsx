import { useEffect, useState } from 'react';
import { niceMax, shortMoney } from '../utils/chart';

export function LineChart({ data, format, color, label }) {
  const [hover, setHover] = useState(null);
  const W = 720;
  const H = 260;
  const m = { l: 52, r: 16, t: 14, b: 28 };
  if (data.length === 0) return <p className="muted">Chưa có dữ liệu trong khoảng thời gian này</p>;
  const max = niceMax(Math.max(...data.map((d) => d.value)));
  const x = (i) => m.l + (data.length === 1 ? (W - m.l - m.r) / 2 : (i / (data.length - 1)) * (W - m.l - m.r));
  const y = (v) => m.t + (1 - v / max) * (H - m.t - m.b);
  const pts = data.map((d, i) => `${x(i)},${y(d.value)}`).join(' ');
  const area = `${x(0)},${H - m.b} ${pts} ${x(data.length - 1)},${H - m.b}`;
  const ticks = [0, 1, 2, 3, 4].map((i) => (max / 4) * i);
  const step = Math.max(1, Math.ceil(data.length / 7));

  const move = (e) => {
    const r = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - r.left) / r.width) * W;
    const i = Math.round(((px - m.l) / (W - m.l - m.r)) * (data.length - 1));
    setHover(Math.min(data.length - 1, Math.max(0, data.length === 1 ? 0 : i)));
  };
  const h = hover == null ? null : data[hover];

  return (
    <div className="chart-wrap">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={label} onMouseMove={move} onMouseLeave={() => setHover(null)}>
        {ticks.map((t) => (
          <g key={t}>
            <line className="grid" x1={m.l} x2={W - m.r} y1={y(t)} y2={y(t)} />
            <text className="axis" x={m.l - 8} y={y(t) + 4} textAnchor="end">{format === 'money' ? shortMoney(t) : Math.round(t)}</text>
          </g>
        ))}
        {data.map((d, i) => i % step === 0 && (
          <text key={d.label} className="axis" x={x(i)} y={H - 8} textAnchor={i === 0 && data.length > 1 ? 'start' : i + step >= data.length && data.length > 1 ? 'end' : 'middle'}>{d.short}</text>
        ))}
        <polygon points={area} fill={color} opacity="0.12" />
        <polyline points={pts} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" className="draw" />
        {h && (
          <g>
            <line className="cross" x1={x(hover)} x2={x(hover)} y1={m.t} y2={H - m.b} />
            <circle cx={x(hover)} cy={y(h.value)} r="5" fill={color} stroke="var(--surface)" strokeWidth="2" />
          </g>
        )}
      </svg>
      {h && (
        <div className="tip" style={{ left: `${(x(hover) / W) * 100}%`, top: 0 }}>
          <span className="muted">{h.label}</span>
          <b>{format === 'money' ? `${Math.round(h.value).toLocaleString('vi-VN')} ₫` : `${h.value} đơn`}</b>
        </div>
      )}
    </div>
  );
}

export function Donut({ items, centerLabel }) {
  const [active, setActive] = useState(null);
  const total = items.reduce((s, i) => s + i.value, 0);
  const R = 70;
  const C = 2 * Math.PI * R;
  let offset = 0;
  const cur = active == null ? null : items[active];
  return (
    <div className="donut">
      <svg viewBox="0 0 200 200" role="img" aria-label={centerLabel}>
        <circle cx="100" cy="100" r={R} fill="none" stroke="var(--track)" strokeWidth="26" />
        {total > 0 && items.map((it, i) => {
          const len = (it.value / total) * C;
          const el = (
            <circle
              key={it.name}
              cx="100" cy="100" r={R} fill="none" stroke={it.color}
              strokeWidth={active === i ? 32 : 26}
              strokeDasharray={`${Math.max(0, len - (it.value === total ? 0 : 2))} ${C}`}
              strokeDashoffset={-offset}
              transform="rotate(-90 100 100)"
              style={{ transition: 'stroke-width .15s', opacity: active == null || active === i ? 1 : 0.35 }}
              onMouseEnter={() => setActive(i)}
              onMouseLeave={() => setActive(null)}
            />
          );
          offset += len;
          return it.value > 0 ? el : null;
        })}
        <text x="100" y="98" textAnchor="middle" className="donut-num">{cur ? cur.value : total}</text>
        <text x="100" y="118" textAnchor="middle" className="axis">{cur ? `${cur.name} · ${Math.round((cur.value / total) * 100)}%` : centerLabel}</text>
      </svg>
      <ul className="legend">
        {items.map((it, i) => (
          <li key={it.name} onMouseEnter={() => setActive(i)} onMouseLeave={() => setActive(null)} className={active === i ? 'on' : ''}>
            <i style={{ background: it.color }} />
            <span>{it.name}</span>
            <b>{it.value}</b>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function BarList({ items, color, format }) {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setReady(true), 30);
    return () => clearTimeout(t);
  }, []);
  if (items.length === 0) return <p className="muted">Chưa có dữ liệu</p>;
  const max = Math.max(1, ...items.map((i) => i.value));
  const total = items.reduce((s, i) => s + i.value, 0);
  return (
    <div className="bars">
      {items.map((it) => (
        <div key={it.name} className="bar-item" title={`${it.name}: ${format ? format(it.value) : it.value} (${Math.round((it.value / total) * 100)}%)`}>
          <span className="bar-name">{it.name}</span>
          <div className="bar-track"><i style={{ width: ready ? `${(it.value / max) * 100}%` : 0, background: color }} /></div>
          <b>{format ? format(it.value) : it.value}</b>
        </div>
      ))}
    </div>
  );
}

export function StackBar({ items }) {
  const total = items.reduce((s, i) => s + i.value, 0) || 1;
  return (
    <>
      <div className="stack">
        {items.filter((i) => i.value > 0).map((i) => (
          <i key={i.name} style={{ flex: i.value, background: i.color }} title={`${i.name}: ${i.value} (${Math.round((i.value / total) * 100)}%)`} />
        ))}
      </div>
      <ul className="legend row-legend">
        {items.map((i) => (
          <li key={i.name}><i style={{ background: i.color }} /><span>{i.name}</span><b>{i.value}</b></li>
        ))}
      </ul>
    </>
  );
}
