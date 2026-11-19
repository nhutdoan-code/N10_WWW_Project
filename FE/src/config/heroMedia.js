// Video giới thiệu cho banner trang chủ, khai báo theo mã sản phẩm.
// File đặt trong FE/public/videos/<mã>.mp4 (+ ảnh poster <mã>.jpg).
// Sản phẩm không có video sẽ hiển thị ảnh sản phẩm với chuyển động chậm thay thế.
// Tạo nhanh video từ ảnh catalog: bash scripts/tao-video-hero.sh <mã> <ảnh...>
export const HERO_VIDEOS = {
  SP500498: { src: '/videos/SP500498.mp4', poster: '/videos/SP500498.jpg' },
  SP500497: { src: '/videos/SP500497.mp4', poster: '/videos/SP500497.jpg' },
  SP500488: { src: '/videos/SP500488.mp4', poster: '/videos/SP500488.jpg' },
  SP500481: { src: '/videos/SP500481.mp4', poster: '/videos/SP500481.jpg' },
};

// Thời lượng mỗi banner (ms). Trùng độ dài video mẫu (8 giây).
export const HERO_INTERVAL = 8000;
