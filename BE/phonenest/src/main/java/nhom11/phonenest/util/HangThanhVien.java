package nhom11.phonenest.util;

public final class HangThanhVien {

    private HangThanhVien() {
    }

    public static String of(int diem) {
        if (diem >= 2000) {
            return "Kim cương";
        }
        if (diem >= 500) {
            return "Vàng";
        }
        return diem >= 100 ? "Bạc" : "Đồng";
    }
}
