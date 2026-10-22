package nhom10.phonenest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import nhom10.phonenest.enums.TrangThaiSanPham;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SanPhamDto(
        String maSanPham,
        @NotBlank(message = "Vui lòng chọn loại sản phẩm") String maLoai,
        String tenLoai,
        @NotBlank(message = "Vui lòng nhập tên máy") String tenMay,
        @NotNull(message = "Vui lòng nhập giá bán") @Positive(message = "Giá bán phải lớn hơn 0") BigDecimal giaBan,
        String moTa,
        @Valid ThongSo thongSo,
        @Min(value = 0, message = "Số lượng tồn không được âm") int soLuongTon,
        TrangThaiSanPham trangThai,
        BigDecimal diemDanhGiaTb,
        String anhChinh,
        List<String> hinhAnhs) {

    public record ThongSo(
            @Positive(message = "Kích thước màn hình phải lớn hơn 0") BigDecimal kichThuocManHinh,
            String loaiManHinh,
            @Positive(message = "Tần số quét phải lớn hơn 0") Integer tanSoQuet,
            String chip,
            @Positive(message = "RAM phải lớn hơn 0") Integer ram,
            @Positive(message = "Bộ nhớ trong phải lớn hơn 0") Integer boNho,
            String cameraSau,
            @Positive(message = "Camera trước phải lớn hơn 0") Integer cameraTruoc,
            @Positive(message = "Dung lượng pin phải lớn hơn 0") Integer pin,
            @Positive(message = "Công suất sạc phải lớn hơn 0") Integer congSuatSac,
            String heDieuHanh,
            boolean nfc,
            boolean ho5G,
            @Positive(message = "Trọng lượng phải lớn hơn 0") Integer trongLuong) {

        public String tomTat() {
            return Stream.of(
                            kichThuocManHinh == null ? null : "Màn hình " + kichThuocManHinh + " inch " + nz(loaiManHinh) + (tanSoQuet == null ? "" : " " + tanSoQuet + "Hz"),
                            chip == null ? null : "Chip " + chip,
                            ram == null ? null : "RAM " + ram + "GB",
                            boNho == null ? null : "Bộ nhớ " + boNho + "GB",
                            cameraSau == null ? null : "Camera " + cameraSau,
                            pin == null ? null : "Pin " + pin + "mAh",
                            nfc ? "NFC" : null,
                            ho5G ? "5G" : null)
                    .filter(Objects::nonNull).collect(Collectors.joining(" | "));
        }

        private static String nz(String x) {
            return x == null ? "" : x;
        }
    }
}
