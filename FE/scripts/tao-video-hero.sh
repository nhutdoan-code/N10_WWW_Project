#!/usr/bin/env bash
# Tạo video ngắn (khoảng 8 giây, không tiếng) cho banner trang chủ từ ảnh màu sắc của một sản phẩm.
# Mỗi ảnh màu được phóng chậm rồi chuyển mờ sang ảnh kế tiếp, nền xám nhạt trùng nền banner.
#
# Cách dùng (chạy trong thư mục FE, cần cài ffmpeg):
#   bash scripts/tao-video-hero.sh SP500498 ../BE/phonenest/src/main/resources/catalog/SP500498/*.webp
# Kết quả: public/videos/SP500498.mp4 và public/videos/SP500498.jpg (ảnh poster).
# Sau đó khai báo mã sản phẩm trong src/config/heroMedia.js.
#
# Có video quay thật (MP4 H.264, 16:9, dưới ~3 MB) thì chép thẳng vào public/videos/<mã>.mp4, không cần script này.
set -euo pipefail

MA="$1"; shift
ANH=("$@")
if [ "${#ANH[@]}" -lt 1 ]; then echo "Cần ít nhất 1 ảnh" >&2; exit 1; fi

NEN="0xECEEEB"      # trùng màu --hero-stage trong main.css
W=1280; H=720
MOI_ANH=3.2         # số giây mỗi ảnh
CHUYEN=0.8          # số giây chuyển cảnh
FPS=30
OUT_DIR="$(dirname "$0")/../public/videos"
mkdir -p "$OUT_DIR"

INPUTS=()
FILTER=""
N=${#ANH[@]}
for i in "${!ANH[@]}"; do
  INPUTS+=(-loop 1 -t "$MOI_ANH" -i "${ANH[$i]}")
  FRAMES=$(awk "BEGIN{print int($MOI_ANH*$FPS)}")
  # Ảnh nền trắng: đặt lên khung trắng rồi nhân (multiply) với màu nền để nền trắng hòa vào màu banner.
  # Phóng to x2 trước khi zoompan để chuyển động mượt, không giật.
  FILTER+="[$i:v]scale=-2:$((H*2*82/100)):flags=lanczos,pad=$((W*2)):$((H*2)):(ow-iw)/2+$((W*2*12/100)):(oh-ih)/2:white,"
  FILTER+="zoompan=z='1+0.07*on/$FRAMES':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=${W}x${H}:fps=$FPS,format=gbrp[z$i];"
  FILTER+="color=c=$NEN:s=${W}x${H}:r=$FPS:d=$MOI_ANH,format=gbrp[c$i];"
  FILTER+="[z$i][c$i]blend=all_mode=multiply,format=yuv420p,setsar=1[v$i];"
done

if [ "$N" -eq 1 ]; then
  FILTER+="[v0]copy[out]"
else
  PREV="v0"
  for ((i=1; i<N; i++)); do
    OFFSET=$(awk "BEGIN{print $i*($MOI_ANH-$CHUYEN)}")
    FILTER+="[$PREV][v$i]xfade=transition=fade:duration=$CHUYEN:offset=$OFFSET[x$i];"
    PREV="x$i"
  done
  FILTER+="[$PREV]copy[out]"
fi

ffmpeg -y -loglevel error "${INPUTS[@]}" -filter_complex "$FILTER" -map "[out]" \
  -c:v libx264 -preset veryfast -crf 26 -pix_fmt yuv420p -movflags +faststart -an \
  "$OUT_DIR/$MA.mp4"
ffmpeg -y -loglevel error -i "$OUT_DIR/$MA.mp4" -frames:v 1 -q:v 4 "$OUT_DIR/$MA.jpg"
echo "Đã tạo $OUT_DIR/$MA.mp4"
