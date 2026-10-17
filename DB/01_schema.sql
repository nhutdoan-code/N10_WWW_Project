CREATE DATABASE IF NOT EXISTS BanDienThoai
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE BanDienThoai;

CREATE TABLE TaiKhoan (
    maTk VARCHAR(50) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    matKhau VARCHAR(255) NOT NULL,
    ngayTao DATE NOT NULL,
    trangThaiHoatDong BOOLEAN NOT NULL DEFAULT TRUE,
    loaiDangNhap VARCHAR(50),
    soDienThoai VARCHAR(20)
);

CREATE TABLE KhachHang (
    maKh VARCHAR(50) PRIMARY KEY,
    hoTen VARCHAR(100) NOT NULL,
    ngaySinh DATE,
    anhDaiDien VARCHAR(500),
    diemTichLuy INT NOT NULL DEFAULT 0,
    hangThanhVien VARCHAR(50),
    maGioiThieu VARCHAR(50),

    CONSTRAINT FK_KhachHang_TaiKhoan
        FOREIGN KEY (maKh)
        REFERENCES TaiKhoan(maTk)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE QuanTriVien (
    maQtv VARCHAR(50) PRIMARY KEY,
    hoTen VARCHAR(100) NOT NULL,
    vaiTro VARCHAR(100),
    chucVu VARCHAR(100),

    CONSTRAINT FK_QuanTriVien_TaiKhoan
        FOREIGN KEY (maQtv)
        REFERENCES TaiKhoan(maTk)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE ThongBao (
    maThongBao VARCHAR(50) PRIMARY KEY,
    maTk VARCHAR(50) NOT NULL,
    noiDung TEXT NOT NULL,
    daDoc BOOLEAN NOT NULL DEFAULT FALSE,
    ngayTao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT FK_ThongBao_TaiKhoan
        FOREIGN KEY (maTk)
        REFERENCES TaiKhoan(maTk)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE DiaChi (
    maDiaChi VARCHAR(50) PRIMARY KEY,
    maKh VARCHAR(50) NOT NULL,
    tenNguoiNhan VARCHAR(100) NOT NULL,
    soDienThoaiNhan VARCHAR(20) NOT NULL,
    diaChiNhanHang VARCHAR(255) NOT NULL,
    tinhThanhPho VARCHAR(100),
    macDinh BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT FK_DiaChi_KhachHang
        FOREIGN KEY (maKh)
        REFERENCES KhachHang(maKh)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE LoaiSanPham (
    maLoai VARCHAR(50) PRIMARY KEY,
    tenLoai VARCHAR(100) NOT NULL,
    moTa TEXT,
    maLoaiCha VARCHAR(50) NULL,

    CONSTRAINT FK_LoaiSanPham_LoaiCha
        FOREIGN KEY (maLoaiCha)
        REFERENCES LoaiSanPham(maLoai)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE TABLE SanPham (
    maSanPham VARCHAR(50) PRIMARY KEY,
    maLoai VARCHAR(50) NOT NULL,
    tenMay VARCHAR(150) NOT NULL,
    giaBan DECIMAL(15,2) NOT NULL,
    moTa TEXT,
    thongSoKyThuat TEXT,
    soLuongTon INT NOT NULL DEFAULT 0,

    trangThai ENUM(
        'CON_HANG',
        'HET_HANG',
        'NGUNG_KINH_DOANH'
    ) NOT NULL DEFAULT 'CON_HANG',

    diemDanhGiaTb DECIMAL(3,2) DEFAULT 0,

    CONSTRAINT FK_SanPham_LoaiSanPham
        FOREIGN KEY (maLoai)
        REFERENCES LoaiSanPham(maLoai)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

CREATE TABLE HinhAnhSanPham (
    maHinhAnh VARCHAR(50) PRIMARY KEY,
    maSanPham VARCHAR(50) NOT NULL,
    duongDan VARCHAR(500) NOT NULL,
    gocChup VARCHAR(100),
    anhChinh BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT FK_HinhAnhSanPham_SanPham
        FOREIGN KEY (maSanPham)
        REFERENCES SanPham(maSanPham)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE GioHang (
    maGioHang VARCHAR(50) PRIMARY KEY,
    maKh VARCHAR(50) NOT NULL UNIQUE,
    ngayTao DATE NOT NULL,

    CONSTRAINT FK_GioHang_KhachHang
        FOREIGN KEY (maKh)
        REFERENCES KhachHang(maKh)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE ChiTietGioHang (
    maGioHang VARCHAR(50) NOT NULL,
    maSanPham VARCHAR(50) NOT NULL,
    soLuong INT NOT NULL DEFAULT 1,

    PRIMARY KEY (maGioHang, maSanPham),

    CONSTRAINT FK_ChiTietGioHang_GioHang
        FOREIGN KEY (maGioHang)
        REFERENCES GioHang(maGioHang)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT FK_ChiTietGioHang_SanPham
        FOREIGN KEY (maSanPham)
        REFERENCES SanPham(maSanPham)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT CK_ChiTietGioHang_SoLuong
        CHECK (soLuong > 0)
);

CREATE TABLE KhuyenMai (
    maKm VARCHAR(50) PRIMARY KEY,
    tenKm VARCHAR(150) NOT NULL,
    phanTramGiam DECIMAL(5,2) NOT NULL,
    soTienGiamToiDa DECIMAL(15,2),
    ngayBatDau DATE NOT NULL,
    ngayKetThuc DATE NOT NULL,
    dieuKienApDung TEXT
);

CREATE TABLE DonHang (
    maDonHang VARCHAR(50) PRIMARY KEY,
    maKh VARCHAR(50) NOT NULL,
    maKm VARCHAR(50),
    diaChiGiaoHang VARCHAR(500),
    ngayCapNhat DATETIME NULL,

    ngayMua DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    phuongThucTt ENUM(
        'COD',
        'CHUYEN_KHOAN',
        'VI_DIEN_TU'
    ) NOT NULL,

    trangThai ENUM(
        'CHO_XAC_NHAN',
        'DANG_XU_LY',
        'DANG_GIAO',
        'DA_GIAO',
        'DA_HUY'
    ) NOT NULL DEFAULT 'CHO_XAC_NHAN',

    CONSTRAINT FK_DonHang_KhachHang
        FOREIGN KEY (maKh)
        REFERENCES KhachHang(maKh)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT FK_DonHang_KhuyenMai
        FOREIGN KEY (maKm)
        REFERENCES KhuyenMai(maKm)
        ON UPDATE CASCADE
        ON DELETE SET NULL
);

CREATE TABLE ChiTietDonHang (
    maDonHang VARCHAR(50) NOT NULL,
    maSanPham VARCHAR(50) NOT NULL,
    soLuong INT NOT NULL,
    donGia DECIMAL(15,2) NOT NULL,

    PRIMARY KEY (maDonHang, maSanPham),

    CONSTRAINT FK_ChiTietDonHang_DonHang
        FOREIGN KEY (maDonHang)
        REFERENCES DonHang(maDonHang)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT FK_ChiTietDonHang_SanPham
        FOREIGN KEY (maSanPham)
        REFERENCES SanPham(maSanPham)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT CK_ChiTietDonHang_SoLuong
        CHECK (soLuong > 0),

    CONSTRAINT CK_ChiTietDonHang_DonGia
        CHECK (donGia >= 0)
);

CREATE TABLE YeuCauDoiTra (
    maYeuCau VARCHAR(50) PRIMARY KEY,
    maDonHang VARCHAR(50) NOT NULL UNIQUE,
    lyDo TEXT,
    hinhAnhMinhChung VARCHAR(500),
    ngayTao DATE NOT NULL,

    trangThai ENUM(
        'CHO_DUYET',
        'DA_DUYET',
        'TU_CHOI',
        'DA_HOAN_TIEN'
    ) NOT NULL DEFAULT 'CHO_DUYET',

    CONSTRAINT FK_YeuCauDoiTra_DonHang
        FOREIGN KEY (maDonHang)
        REFERENCES DonHang(maDonHang)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE DanhGia (
    maDanhGia VARCHAR(50) PRIMARY KEY,
    maKh VARCHAR(50) NOT NULL,
    maSanPham VARCHAR(50) NOT NULL,

    diemSao INT NOT NULL,
    noiDungBinhLuan TEXT,
    hinhAnhThucTe VARCHAR(500),
    ngayDanhGia DATE NOT NULL,

    CONSTRAINT FK_DanhGia_KhachHang
        FOREIGN KEY (maKh)
        REFERENCES KhachHang(maKh)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT FK_DanhGia_SanPham
        FOREIGN KEY (maSanPham)
        REFERENCES SanPham(maSanPham)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT CK_DanhGia_DiemSao
        CHECK (diemSao BETWEEN 1 AND 5)
);

CREATE TABLE HoiDap (
    maCauHoi VARCHAR(50) PRIMARY KEY,
    maKh VARCHAR(50) NOT NULL,
    maSanPham VARCHAR(50) NOT NULL,

    noiDungCauHoi TEXT NOT NULL,
    noiDungTraLoi TEXT,
    ngayHoi DATE NOT NULL,
    ngayTraLoi DATE,

    CONSTRAINT FK_HoiDap_KhachHang
        FOREIGN KEY (maKh)
        REFERENCES KhachHang(maKh)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT FK_HoiDap_SanPham
        FOREIGN KEY (maSanPham)
        REFERENCES SanPham(maSanPham)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE Wishlist (
    maDanhSach VARCHAR(50) PRIMARY KEY,
    maKh VARCHAR(50) NOT NULL UNIQUE,

    CONSTRAINT FK_Wishlist_KhachHang
        FOREIGN KEY (maKh)
        REFERENCES KhachHang(maKh)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE WishlistSanPham (
    maDanhSach VARCHAR(50) NOT NULL,
    maSanPham VARCHAR(50) NOT NULL,

    PRIMARY KEY (maDanhSach, maSanPham),

    CONSTRAINT FK_WishlistSanPham_Wishlist
        FOREIGN KEY (maDanhSach)
        REFERENCES Wishlist(maDanhSach)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT FK_WishlistSanPham_SanPham
        FOREIGN KEY (maSanPham)
        REFERENCES SanPham(maSanPham)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);