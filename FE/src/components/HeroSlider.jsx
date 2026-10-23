import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from './Icon';

const INTERVAL = 5000;

export default function HeroSlider({ slides }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduce = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const go = (i) => setIndex((i + slides.length) % slides.length);

  useEffect(() => {
    if (paused || reduce || slides.length < 2) return undefined;
    const t = setTimeout(() => go(index + 1), INTERVAL);
    return () => clearTimeout(t);
  }, [index, paused, reduce, slides.length]);

  return (
    <div
      className="slider"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      aria-roledescription="carousel"
    >
      {slides.map((s, i) => (
        <article key={s.id} className={`slide tone-${s.tone} ${i === index ? 'on' : ''}`} aria-hidden={i !== index}>
          <div className="slide-text">
            <span className="slide-tag">{s.tag}</span>
            <h2>{s.title}</h2>
            <p>{s.text}</p>
            <Link to={s.to} className="slide-cta" tabIndex={i === index ? 0 : -1}>{s.cta}</Link>
          </div>
          {s.image && (
            <Link to={s.to} className="slide-visual" tabIndex={-1} aria-hidden="true">
              <img src={s.image} alt="" />
              {s.price && <span className="slide-price">{s.price}</span>}
            </Link>
          )}
        </article>
      ))}
      {slides.length > 1 && (
        <>
          <button className="slide-nav prev" aria-label="Banner trước" onClick={() => go(index - 1)}><Icon name="left" /></button>
          <button className="slide-nav next" aria-label="Banner tiếp" onClick={() => go(index + 1)}><Icon name="right" /></button>
          <div className="slide-dots">
            {slides.map((s, i) => (
              <button key={s.id} aria-label={`Banner ${i + 1}`} className={i === index ? 'on' : ''} onClick={() => go(i)}>
                {i === index && !paused && !reduce && <i key={index} style={{ animationDuration: `${INTERVAL}ms` }} />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
