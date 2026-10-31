package nhom10.phonenest.service;

import lombok.RequiredArgsConstructor;
import nhom10.phonenest.dto.GioHangDto;
import nhom10.phonenest.dto.SanPhamDto;
import nhom10.phonenest.entity.ChiTietGioHang;
import nhom10.phonenest.entity.GioHang;
import nhom10.phonenest.entity.KhachHang;
import nhom10.phonenest.entity.PhienBanSanPham;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.mapper.SanPhamMapper;
import nhom10.phonenest.repository.ChiTietGioHangRepository;
import nhom10.phonenest.repository.GioHangRepository;
import nhom10.phonenest.repository.KhachHangRepository;
import nhom10.phonenest.repository.PhienBanSanPhamRepository;
import nhom10.phonenest.util.IdGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GioHangService {

 private final GioHangRepository gioHangRepo;
 private final ChiTietGioHangRepository chiTietGioHangRepo;
 private final KhachHangRepository khachHangRepo;
 private final PhienBanSanPhamRepository phienBanRepo;

 // ==================== LẤY GIỎ HÀNG ====================

 public GioHangDto get(String maKhachHang) {
  GioHang gioHang = getOrCreate(maKhachHang);
  return toDto(gioHang);
 }

 // ==================== THÊM VÀO GIỎ HÀNG ====================

 public GioHangDto add(
         String maKhachHang,
         String maPhienBan,
         int soLuong
 ) {
  if (soLuong < 1) {
   throw new AppException(
           HttpStatus.BAD_REQUEST,
           "Số lượng phải lớn hơn 0"
   );
  }

  GioHang gioHang = getOrCreate(maKhachHang);
  PhienBanSanPham phienBan = findPhienBan(maPhienBan);

  ChiTietGioHang.Id id = new ChiTietGioHang.Id(
          gioHang.getMaGioHang(),
          maPhienBan
  );

  ChiTietGioHang chiTiet = chiTietGioHangRepo.findById(id)
          .orElse(null);

  int soLuongHienTai = chiTiet == null
          ? 0
          : chiTiet.getSoLuong();

  int soLuongMoi = soLuongHienTai + soLuong;

  validateSoLuong(phienBan, soLuongMoi);

  if (chiTiet == null) {
   chiTiet = new ChiTietGioHang();

   chiTiet.setId(id);
   chiTiet.setGioHang(gioHang);
   chiTiet.setPhienBan(phienBan);
  }

  chiTiet.setSoLuong(soLuongMoi);

  chiTietGioHangRepo.save(chiTiet);

  return toDto(gioHang);
 }

 // ==================== CẬP NHẬT SỐ LƯỢNG ====================

 public GioHangDto update(
         String maKhachHang,
         String maPhienBan,
         int soLuong
 ) {
  GioHang gioHang = getOrCreate(maKhachHang);

  ChiTietGioHang chiTiet = findChiTiet(
          gioHang.getMaGioHang(),
          maPhienBan
  );

  // Nếu số lượng <= 0 thì xóa khỏi giỏ hàng.
  if (soLuong <= 0) {
   chiTietGioHangRepo.delete(chiTiet);
   return toDto(gioHang);
  }

  validateSoLuong(chiTiet.getPhienBan(), soLuong);

  chiTiet.setSoLuong(soLuong);

  chiTietGioHangRepo.save(chiTiet);

  return toDto(gioHang);
 }

 // ==================== XÓA MỘT SẢN PHẨM ====================

 public GioHangDto remove(
         String maKhachHang,
         String maPhienBan
 ) {
  GioHang gioHang = getOrCreate(maKhachHang);

  ChiTietGioHang.Id id = new ChiTietGioHang.Id(
          gioHang.getMaGioHang(),
          maPhienBan
  );

  chiTietGioHangRepo.deleteById(id);

  return toDto(gioHang);
 }

 // ==================== XÓA TOÀN BỘ GIỎ HÀNG ====================

 public GioHangDto clear(String maKhachHang) {
  GioHang gioHang = getOrCreate(maKhachHang);

  chiTietGioHangRepo.xoaTheoMaGioHang(
          gioHang.getMaGioHang()
  );

  return toDto(gioHang);
 }

 // ==================== ĐỒNG BỘ GIỎ HÀNG ====================

 public GioHangDto sync(
         String maKhachHang,
         List<GioHangDto.Request> requests
 ) {
  GioHang gioHang = getOrCreate(maKhachHang);

  if (requests == null || requests.isEmpty()) {
   return toDto(gioHang);
  }

  for (GioHangDto.Request request : requests) {

   if (request == null
           || request.maPhienBan() == null
           || request.maPhienBan().isBlank()
           || request.soLuong() < 1) {
    throw new AppException(
            HttpStatus.BAD_REQUEST,
            "Thông tin sản phẩm trong giỏ hàng không hợp lệ"
    );
   }

   String maPhienBan = request.maPhienBan();

   PhienBanSanPham phienBan = findPhienBan(maPhienBan);

   ChiTietGioHang.Id id = new ChiTietGioHang.Id(
           gioHang.getMaGioHang(),
           maPhienBan
   );

   ChiTietGioHang chiTiet = chiTietGioHangRepo.findById(id)
           .orElse(null);

   int soLuongHienTai = chiTiet == null
           ? 0
           : chiTiet.getSoLuong();

   int soLuongMoi = soLuongHienTai + request.soLuong();

   validateSoLuong(phienBan, soLuongMoi);

   if (chiTiet == null) {
    chiTiet = new ChiTietGioHang();

    chiTiet.setId(id);
    chiTiet.setGioHang(gioHang);
    chiTiet.setPhienBan(phienBan);
   }

   chiTiet.setSoLuong(soLuongMoi);

   chiTietGioHangRepo.save(chiTiet);
  }

  return toDto(gioHang);
 }

 // ==================== TÌM PHIÊN BẢN ====================

 private PhienBanSanPham findPhienBan(String maPhienBan) {
  return phienBanRepo.findById(maPhienBan)
          .orElseThrow(() -> new AppException(
                  HttpStatus.NOT_FOUND,
                  "Không tìm thấy phiên bản sản phẩm"
          ));
 }

 // ==================== TÌM CHI TIẾT GIỎ HÀNG ====================

 private ChiTietGioHang findChiTiet(
         String maGioHang,
         String maPhienBan
 ) {
  ChiTietGioHang.Id id = new ChiTietGioHang.Id(
          maGioHang,
          maPhienBan
  );

  return chiTietGioHangRepo.findById(id)
          .orElseThrow(() -> new AppException(
                  HttpStatus.NOT_FOUND,
                  "Sản phẩm không có trong giỏ hàng"
          ));
 }

 // ==================== KIỂM TRA TỒN KHO ====================

 private void validateSoLuong(
         PhienBanSanPham phienBan,
         int soLuong
 ) {
  SanPham sanPham = phienBan.getSanPham();

  if (sanPham.getTrangThai()
          == TrangThaiSanPham.NGUNG_KINH_DOANH) {
   throw new AppException(
           HttpStatus.BAD_REQUEST,
           "Sản phẩm đã ngừng kinh doanh"
   );
  }

  if (phienBan.getTrangThai()
          != TrangThaiSanPham.CON_HANG) {
   throw new AppException(
           HttpStatus.BAD_REQUEST,
           "Phiên bản sản phẩm hiện không còn kinh doanh"
   );
  }

  if (soLuong < 1) {
   throw new AppException(
           HttpStatus.BAD_REQUEST,
           "Số lượng phải lớn hơn 0"
   );
  }

  if (soLuong > phienBan.getSoLuongTon()) {
   throw new AppException(
           HttpStatus.BAD_REQUEST,
           "Số lượng vượt quá tồn kho của phiên bản"
   );
  }
 }

 // ==================== TÌM HOẶC TẠO GIỎ HÀNG ====================

 private GioHang getOrCreate(String maKhachHang) {
  return gioHangRepo.findByKhachHangMaKh(maKhachHang)
          .orElseGet(() -> {
           KhachHang khachHang = khachHangRepo.findById(maKhachHang)
                   .orElseThrow(() -> new AppException(
                           HttpStatus.NOT_FOUND,
                           "Không tìm thấy khách hàng"
                   ));

           GioHang gioHang = new GioHang();
           gioHang.setMaGioHang(IdGenerator.next("GH"));
           gioHang.setKhachHang(khachHang);
           gioHang.setNgayTao(LocalDateTime.now());
           gioHang.setNgayCapNhat(LocalDateTime.now());

           return gioHangRepo.save(gioHang);
          });
 }
 // ==================== CHUYỂN SANG DTO ====================

 private GioHangDto toDto(GioHang gioHang) {

  List<ChiTietGioHang> chiTietList =
          chiTietGioHangRepo.findByGioHangMaGioHang(
                  gioHang.getMaGioHang()
          );

  List<GioHangDto.Item> items = new ArrayList<>();

  BigDecimal tongTien = BigDecimal.ZERO;

  for (ChiTietGioHang chiTiet : chiTietList) {

   PhienBanSanPham phienBan = chiTiet.getPhienBan();

   SanPham sanPham = phienBan.getSanPham();

   SanPhamDto sanPhamDto = SanPhamMapper.toDto(sanPham);

   SanPhamDto.PhienBan phienBanDto =
           new SanPhamDto.PhienBan(
                   phienBan.getMaPhienBan(),
                   phienBan.getSku(),
                   phienBan.getRam(),
                   phienBan.getBoNho(),
                   phienBan.getMauSac(),
                   phienBan.getGiaBan(),
                   phienBan.getGiaNiemYet(),
                   phienBan.getSoLuongTon(),
                   phienBan.getTrangThai()
           );

   items.add(
           new GioHangDto.Item(
                   sanPhamDto,
                   phienBanDto,
                   chiTiet.getSoLuong()
           )
   );

   BigDecimal thanhTien = phienBan.getGiaBan()
           .multiply(BigDecimal.valueOf(chiTiet.getSoLuong()));

   tongTien = tongTien.add(thanhTien);
  }

  return new GioHangDto(items, tongTien);
 }
}