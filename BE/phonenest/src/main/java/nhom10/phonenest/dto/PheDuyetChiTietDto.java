package nhom10.phonenest.dto;

import nhom10.phonenest.enums.LyDoKhoaBan;
import nhom10.phonenest.enums.TrangThaiPheDuyet;
import nhom10.phonenest.enums.TrangThaiSanPham;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Thong tin phe duyet noi bo — CHI tra ve qua /api/admin/** (ADMIN). */
public record PheDuyetChiTietDto(
        String maPhienBan,
        String maSanPham,
        String tenMay,
        String maThuongHieu,
        String tenThuongHieu,
        String sku,
        int ram,
        int boNho,
        String mauSac,
        BigDecimal giaBan,
        BigDecimal giaNiemYet,
        int soLuongTon,
        TrangThaiSanPham trangThai,
        TrangThaiSanPham trangThaiSanPham,
        TrangThaiPheDuyet trangThaiPheDuyet,
        boolean duocPhepBan,
        boolean pheDuyetConHieuLuc,
        String nguoiPheDuyet,
        LocalDateTime ngayPheDuyet,
        String lyDoPheDuyet,
        BigDecimal giaPheDuyet,
        BigDecimal giaNiemYetPheDuyet,
        String nguonXacMinh,
        LyDoKhoaBan lyDoKhoa,
        BigDecimal giaBanKhiKhoa,
        /** APPROVED nhung dau van tay khong con khop -> KHONG duoc ban, can canh bao rieng. */
        boolean canhBaoPheDuyetLech,
        /** Ma kiem tra dong thoi: gui lai khi duyet/khoa/mo khoa. */
        String maKiemTra
) {
}
