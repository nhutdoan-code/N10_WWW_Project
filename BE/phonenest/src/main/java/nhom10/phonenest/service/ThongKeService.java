package nhom10.phonenest.service;

import nhom10.phonenest.dto.DonHangDto;
import nhom10.phonenest.dto.ThongKeDto;
import nhom10.phonenest.entity.DonHang;
import nhom10.phonenest.entity.SanPham;
import nhom10.phonenest.enums.TrangThaiDonHang;
import nhom10.phonenest.enums.TrangThaiSanPham;
import nhom10.phonenest.mapper.DonHangMapper;
import nhom10.phonenest.repository.DonHangRepository;
import nhom10.phonenest.repository.LoaiSanPhamRepository;
import nhom10.phonenest.repository.SanPhamRepository;
import nhom10.phonenest.repository.YeuCauDoiTraRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThongKeService {

    private final DonHangRepository donHangRepository;
    private final SanPhamRepository sanPhamRepository;
    private final YeuCauDoiTraRepository yeuCauDoiTraRepository;
    private final LoaiSanPhamRepository loaiSanPhamRepository;
    private final DonHangMapper donHangMapper;

    public ThongKeDto thongKe(LocalDate tu, LocalDate den) {
        List<DonHang> dons = trongKhoang(tu, den);
        List<DonHangDto> dtos = dons.stream().map(donHangMapper::toDto).toList();

        Map<String, Integer> theoTrangThai = new LinkedHashMap<>();
        for (TrangThaiDonHang t : TrangThaiDonHang.values()) {
            theoTrangThai.put(t.name(), 0);
        }
        dtos.forEach(d -> theoTrangThai.merge(d.trangThai().name(), 1, Integer::sum));

        BigDecimal doanhThu = BigDecimal.ZERO;
        Map<String, BigDecimal> theoNgay = new TreeMap<>();
        Map<String, Integer> soDonNgay = new TreeMap<>();
        Map<String, BigDecimal> tienHang = new HashMap<>();
        Map<String, Integer> slHang = new HashMap<>();
        Map<String, Integer> daBan = new HashMap<>();
        for (DonHangDto d : dtos) {
            String ngayMua = d.ngayMua().toLocalDate().toString();
            if (d.trangThai() != TrangThaiDonHang.DA_HUY) {
                soDonNgay.merge(ngayMua, 1, Integer::sum);
                theoNgay.putIfAbsent(ngayMua, BigDecimal.ZERO);
            }
            if (d.trangThai() == TrangThaiDonHang.DA_GIAO) {
                doanhThu = doanhThu.add(d.tongTien());
                theoNgay.merge(ngayMua, d.tongTien(), BigDecimal::add);
                d.chiTiet().forEach(c -> {
                    String hang = tenHang(c.maSanPham());
                    tienHang.merge(hang, c.donGia().multiply(BigDecimal.valueOf(c.soLuong())), BigDecimal::add);
                    slHang.merge(hang, c.soLuong(), Integer::sum);
                });
            }
            if (d.trangThai() != TrangThaiDonHang.DA_HUY) {
                d.chiTiet().forEach(c -> daBan.merge(c.maSanPham(), c.soLuong(), Integer::sum));
            }
        }
        List<ThongKeDto.BanChay> banChay = daBan.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new ThongKeDto.BanChay(e.getKey(),
                        sanPhamRepository.findById(e.getKey()).map(SanPham::getTenMay).orElse(""), e.getValue()))
                .toList();
        List<ThongKeDto.Ngay> ngay = new ArrayList<>();
        theoNgay.forEach((k, v) -> ngay.add(new ThongKeDto.Ngay(k, v, soDonNgay.getOrDefault(k, 0))));
        List<ThongKeDto.Hang> theoHang = tienHang.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> new ThongKeDto.Hang(e.getKey(), e.getValue(), slHang.get(e.getKey()))).toList();

        int soDoiTra = (int) yeuCauDoiTraRepository.findAll().stream()
                .filter(y -> !y.getNgayTao().toLocalDate().isBefore(batDau(tu)) && !y.getNgayTao().toLocalDate().isAfter(ketThuc(den)))
                .count();
        List<SanPham> sanPhams = sanPhamRepository.findAll().stream()
                .filter(s -> s.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH)
                .toList();
        int sapHet = (int) sanPhams.stream()
                .filter(s -> tongTon(s) > 0 && tongTon(s) <= KhoService.NGUONG_SAP_HET).count();
        int hetHang = (int) sanPhams.stream().filter(s -> tongTon(s) == 0).count();
        int conHang = sanPhams.size() - sapHet - hetHang;

        return new ThongKeDto(doanhThu, dtos.size(), theoTrangThai.get(TrangThaiDonHang.DA_HUY.name()), soDoiTra,
                sapHet, hetHang, conHang, theoTrangThai, banChay, ngay, theoHang);
    }

    private int tongTon(SanPham sanPham) {
        // Chỉ tính variant đang kinh doanh, đồng bộ với SanPham.getSoLuongTon().
        return sanPham.getSoLuongTon();
    }

    private String tenHang(String maSanPham) {
        var loai = sanPhamRepository.findById(maSanPham).map(SanPham::getLoai).orElse(null);
        while (loai != null && loai.getMaLoaiCha() != null) {
            loai = loaiSanPhamRepository.findById(loai.getMaLoaiCha()).orElse(null);
        }
        return loai == null ? "Khác" : loai.getTenLoai();
    }

    public String xuatCsv(LocalDate tu, LocalDate den) {
        StringBuilder sb = new StringBuilder("﻿Mã đơn,Ngày mua,Khách hàng,Trạng thái,Thanh toán,Tạm tính,Giảm giá,Phí vận chuyển,Tổng tiền\n");
        trongKhoang(tu, den).stream().map(donHangMapper::toDto).forEach(d -> sb
                .append(d.maDonHang()).append(',')
                .append(d.ngayMua().toLocalDate()).append(',')
                .append('"').append(d.tenKhach().replace("\"", "\"\"")).append('"').append(',')
                .append(d.trangThai()).append(',')
                .append(d.phuongThucTt()).append(',')
                .append(d.tamTinh().toBigInteger()).append(',')
                .append(d.giamGia().toBigInteger()).append(',')
                .append(d.phiVanChuyen().subtract(d.giamPhiVanChuyen()).toBigInteger()).append(',')
                .append(d.tongTien().toBigInteger()).append('\n'));
        return sb.toString();
    }

    private List<DonHang> trongKhoang(LocalDate tu, LocalDate den) {
        return donHangRepository.findAllByOrderByNgayMuaDesc().stream()
                .filter(d -> !d.getNgayMua().toLocalDate().isBefore(batDau(tu))
                        && !d.getNgayMua().toLocalDate().isAfter(ketThuc(den)))
                .toList();
    }

    private LocalDate batDau(LocalDate tu) {
        return tu == null ? LocalDate.of(2000, 1, 1) : tu;
    }

    private LocalDate ketThuc(LocalDate den) {
        return den == null ? LocalDate.of(2999, 12, 31) : den;
    }
}
