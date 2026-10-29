
package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.entity.*;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.SanPhamRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GioHangService {

 private final GioHangRepository gioHangRepository;
 private final SanPhamRepository sanPhamRepository;

 // Lấy giỏ hàng
 public GioHangDto get(String maKh) {
  return toDto(gioHang(maKh));
 }

 // Thêm sản phẩm vào giỏ
 public GioHangDto add(String maKh, String ma, int sl) {
  GioHang g = gioHang(maKh);
  SanPham p = resolve(ma);

  ChiTietGioHang c = find(g, p.getMaSanPham());
  int soLuongMoi = (c == null ? 0 : c.getSoLuong()) + sl;

  return set(g, p, soLuongMoi);
 }

 // Cập nhật số lượng
 public GioHangDto update(String maKh, String ma, int sl) {
  GioHang g = gioHang(maKh);
  SanPham p = resolve(ma);
  ChiTietGioHang c = find(g, p.getMaSanPham());

  if (c == null) {
   if (sl <= 0) {
    return toDto(g);
   }
   return set(g, p, sl);
  }

  if (sl <= 0) {
   g.getChiTiet().remove(c);
  } else {
   validate(p, sl);
   c.setSoLuong(sl);
  }

  touch(g);
  return toDto(g);
 }

 // Xóa một sản phẩm
 public GioHangDto remove(String maKh, String ma) {
  GioHang g = gioHang(maKh);
  String maSanPham = resolve(ma).getMaSanPham();

  g.getChiTiet().removeIf(c ->
          c.getSanPham().getMaSanPham().equals(maSanPham)
  );

  touch(g);
  return toDto(g);
 }

 // Xóa toàn bộ giỏ hàng
 public GioHangDto clear(String maKh) {
  GioHang g = gioHang(maKh);
  g.getChiTiet().clear();
  touch(g);
  return toDto(g);
 }

 // Đồng bộ danh sách sản phẩm từ frontend
 public GioHangDto sync(String maKh, List<GioHangDto.Request> ds) {
  GioHang g = gioHang(maKh);

  for (GioHangDto.Request r : ds) {
   try {
    SanPham p = resolve(r.maSanPham());
    ChiTietGioHang c = find(g, p.getMaSanPham());

    int soLuongMoi =
            (c == null ? 0 : c.getSoLuong()) + r.soLuong();

    validate(p, soLuongMoi);

    if (c == null) {
     addLine(g, p, soLuongMoi);
    } else {
     c.setSoLuong(soLuongMoi);
    }
   } catch (AppException ignored) {
    // Giữ hành vi cũ: bỏ qua sản phẩm không hợp lệ
   }
  }

  touch(g);
  return toDto(g);
 }

 // Thêm hoặc cập nhật một dòng giỏ hàng
 private GioHangDto set(GioHang g, SanPham p, int sl) {
  validate(p, sl);

  ChiTietGioHang c = find(g, p.getMaSanPham());

  if (c == null) {
   addLine(g, p, sl);
  } else {
   c.setSoLuong(sl);
  }

  touch(g);
  return toDto(g);
 }

 // Tạo dòng chi tiết giỏ hàng
 private void addLine(GioHang g, SanPham p, int sl) {
  ChiTietGioHang c = new ChiTietGioHang();

  c.setId(new ChiTietGioHang.Id(
          g.getMaGioHang(),
          p.getMaSanPham()
  ));

  c.setGioHang(g);
  c.setSanPham(p);
  c.setSoLuong(sl);

  g.getChiTiet().add(c);
 }

 // Kiểm tra số lượng và trạng thái sản phẩm
 private void validate(SanPham p, int sl) {
  if (p.getTrangThai() != TrangThaiSanPham.CON_HANG
          || sl < 1
          || sl > p.getSoLuongTon()) {

   throw new AppException(
           HttpStatus.BAD_REQUEST,
           "Số lượng không hợp lệ (còn "
                   + p.getSoLuongTon() + " sản phẩm)"
   );
  }
 }

 // Tìm sản phẩm theo mã sản phẩm
 private SanPham resolve(String ma) {
  return sanPhamRepository.findById(ma)
          .orElseThrow(() -> new AppException(
                  HttpStatus.NOT_FOUND,
                  "Sản phẩm không tồn tại"
          ));
 }

 // Tìm sản phẩm đã có trong giỏ
 private ChiTietGioHang find(GioHang g, String maSanPham) {
  return g.getChiTiet().stream()
          .filter(c -> c.getSanPham() != null
                  && c.getSanPham().getMaSanPham().equals(maSanPham))
          .findFirst()
          .orElse(null);
 }

 // Lấy giỏ hàng của khách
 private GioHang gioHang(String maKh) {
  return gioHangRepository.findByKhachHangMaKh(maKh)
          .orElseThrow(() -> new AppException(
                  HttpStatus.NOT_FOUND,
                  "Không tìm thấy giỏ hàng"
          ));
 }

 // Cập nhật thời gian
 private void touch(GioHang g) {
  g.setNgayCapNhat(LocalDateTime.now());
 }

 // Chuyển giỏ hàng thành DTO
// Chuyển giỏ hàng thành DTO
 private GioHangDto toDto(GioHang g) {
  List<GioHangDto.Item> items = g.getChiTiet().stream()
          .map(c -> new GioHangDto.Item(
                  SanPhamMapper.toDto(c.getSanPham()),
                  null, // Tạm thời chưa có dữ liệu phiên bản
                  c.getSoLuong()
          ))
          .toList();

  BigDecimal tong = g.getChiTiet().stream()
          .map(c -> c.getSanPham().getGiaBan()
                  .multiply(BigDecimal.valueOf(c.getSoLuong())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);

  return new GioHangDto(items, tong);
 }
}
