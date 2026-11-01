package nhom10.phonenest.dto;

public record ThuongHieuDto(
        String maThuongHieu,
        String tenThuongHieu,
        String logo,
        boolean trangThaiHoatDong) {

    public ThuongHieuDto(String maThuongHieu, String tenThuongHieu, String logo) {
        this(maThuongHieu, tenThuongHieu, logo, true);
    }
}
