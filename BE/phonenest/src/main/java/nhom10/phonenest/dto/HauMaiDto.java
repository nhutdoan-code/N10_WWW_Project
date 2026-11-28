package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nhom10.phonenest.enums.LoaiHauMai;
import nhom10.phonenest.enums.TrangThaiHauMai;

import java.time.LocalDateTime;
import java.util.List;

public record HauMaiDto(
        String maYeuCau,
        String maDonHang,
        String maPhienBan,
        String tenSanPham,
        String tenKhach,
        String soDienThoai,
        LoaiHauMai loai,
        String lyDo,
        String hinhAnhMinhChung,
        TrangThaiHauMai trangThai,
        String ghiChuXuLy,
        String ketQuaXuLy,
        LocalDateTime ngayTao,
        LocalDateTime ngayCapNhat,
        List<LichSu> lichSu,
        String trangThaiXacNhanKhach,
        LocalDateTime hanXacNhanKhach,
        String ghiChuXacNhanKhach) {

    @com.fasterxml.jackson.annotation.JsonProperty("hinhAnhMinhChung")
    public String hinhAnhMinhChung() { return anhMinhChung().stream().findFirst().orElse(null); }
    @com.fasterxml.jackson.annotation.JsonProperty("anhMinhChung")
    public List<String> anhMinhChung() { return hinhAnhMinhChung == null ? List.of() : hinhAnhMinhChung.lines().filter(x -> !x.isBlank()).toList(); }

    public record LichSu(TrangThaiHauMai trangThai, String ghiChu, LocalDateTime thoiGian) {}

    public record Request(
            String maDonHang,
            @NotNull(message = "Vui lòng chọn loại yêu cầu") LoaiHauMai loai,
            String maPhienBan,
            @NotBlank(message = "Vui lòng mô tả vấn đề")
            @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự") String lyDo,
            @Size(max = 500, message = "Link minh chứng quá dài") String hinhAnhMinhChung) {}

    public record XuLyRequest(
            @NotNull(message = "Vui lòng chọn trạng thái") TrangThaiHauMai trangThai,
            @NotBlank(message = "Vui lòng nhập nội dung cập nhật")
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String ghiChuXuLy,
            @Size(max = 1000, message = "Kết quả xử lý tối đa 1000 ký tự") String ketQuaXuLy) {}
}
