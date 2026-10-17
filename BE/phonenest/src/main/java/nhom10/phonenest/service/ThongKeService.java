package nhom11.phonenest.service;

import nhom11.phonenest.dto.DonHangDto;
import nhom11.phonenest.dto.ThongKeDto;
import nhom11.phonenest.entity.DonHang;
import nhom11.phonenest.entity.SanPham;
import nhom11.phonenest.enums.TrangThaiDonHang;
import nhom11.phonenest.enums.TrangThaiSanPham;
import nhom11.phonenest.mapper.DonHangMapper;
import nhom11.phonenest.repository.DonHangRepository;
import nhom11.phonenest.repository.SanPhamRepository;
import nhom11.phonenest.repository.YeuCauDoiTraRepository;
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
        Map<String, Integer> daBan = new HashMap<>();
        for (DonHangDto d : dtos) {
            if (d.trangThai() == TrangThaiDonHang.DA_GIAO) {
                doanhThu = doanhThu.add(d.tongTien());
                theoNgay.merge(d.ngayMua().toLocalDate().toString(), d.tongTien(), BigDecimal::add);
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
        theoNgay.forEach((k, v) -> ngay.add(new ThongKeDto.Ngay(k, v)));

        int soDoiTra = (int) yeuCauDoiTraRepository.findAll().stream()
                .filter(y -> !y.getNgayTao().isBefore(batDau(tu)) && !y.getNgayTao().isAfter(ketThuc(den)))
                .count();
        List<SanPham> sanPhams = sanPhamRepository.findAll().stream()
                .filter(s -> s.getTrangThai() != TrangThaiSanPham.NGUNG_KINH_DOANH)
                .toList();
        int sapHet = (int) sanPhams.stream()
                .filter(s -> s.getSoLuongTon() > 0 && s.getSoLuongTon() <= KhoService.NGUONG_SAP_HET).count();
        int hetHang = (int) sanPhams.stream().filter(s -> s.getSoLuongTon() == 0).count();

        return new ThongKeDto(doanhThu, dtos.size(), theoTrangThai.get(TrangThaiDonHang.DA_HUY.name()), soDoiTra,
                sapHet, hetHang, theoTrangThai, banChay, ngay);
    }

    public String xuatCsv(LocalDate tu, LocalDate den) {
        StringBuilder sb = new StringBuilder("﻿Mã đơn,Ngày mua,Khách hàng,Trạng thái,Thanh toán,Tạm tính,Giảm giá,Tổng tiền\n");
        trongKhoang(tu, den).stream().map(donHangMapper::toDto).forEach(d -> sb
                .append(d.maDonHang()).append(',')
                .append(d.ngayMua().toLocalDate()).append(',')
                .append('"').append(d.tenKhach().replace("\"", "\"\"")).append('"').append(',')
                .append(d.trangThai()).append(',')
                .append(d.phuongThucTt()).append(',')
                .append(d.tamTinh().toBigInteger()).append(',')
                .append(d.giamGia().toBigInteger()).append(',')
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
