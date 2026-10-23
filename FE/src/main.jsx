import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles/main.css';

const NO_IMAGE = 'data:image/svg+xml,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300" viewBox="0 0 300 300"><rect width="300" height="300" fill="#f2f4f7"/><rect x="105" y="45" width="90" height="190" rx="14" fill="none" stroke="#c3cad6" stroke-width="6"/><circle cx="150" cy="215" r="6" fill="#c3cad6"/><text x="150" y="268" font-size="15" text-anchor="middle" fill="#98a2b3" font-family="sans-serif">Chưa có ảnh</text></svg>'
);
document.addEventListener('error', (e) => {
  if (e.target.tagName === 'IMG' && e.target.src !== NO_IMAGE) e.target.src = NO_IMAGE;
}, true);

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
