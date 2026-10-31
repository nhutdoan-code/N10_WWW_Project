package nhom10.phonenest.service;
import nhom10.phonenest.mapper.SanPhamMapper;
import lombok.RequiredArgsConstructor; import nhom10.phonenest.dto.*; import nhom10.phonenest.entity.*; import nhom10.phonenest.enums.*; import nhom10.phonenest.exception.AppException; import nhom10.phonenest.mapper.DonHangMapper; import nhom10.phonenest.repository.*; import nhom10.phonenest.util.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.math.BigDecimal; import java.time.*; import java.util.*;
@Service @RequiredArgsConstructor @Transactional public class DonHangService{
 private static final int DIEM_MOI_VND=10000; private static final String LY_DO_KHAC="Lý do khác"; private static final List<String> LY_DO_HUY=List.of("Muốn thay đổi địa chỉ giao hàng","Muốn nhập / thay đổi mã khuyến mãi","Muốn thay đổi sản phẩm trong đơn (màu sắc, dung lượng, số lượng...)","Thủ tục thanh toán quá rắc rối","Tìm thấy giá tốt hơn ở nơi khác","Đổi ý, không muốn mua nữa",LY_DO_KHAC);
 private final DonHangRepository donHangRepository; private final GioHangRepository gioHangRepository; private final PhienBanSanPhamRepository phienBanRepository; private final KhuyenMaiRepository khuyenMaiRepository; private final HuyDonHangRepository huyRepository; private final ThanhToanRepository thanhToanRepository; private final LichSuTrangThaiDonHangRepository lichSuRepository; private final BienDongKhoRepository bienDongRepository; private final KhuyenMaiService khuyenMaiService; private final DiaChiService diaChiService; private final PhiVanChuyenService phiVanChuyenService; private final ThanhToanService thanhToanService; private final KhachHangRepository khachHangRepository; private final TaiKhoanRepository taiKhoanRepository; private final DiaChiRepository diaChiRepository; private final DonHangMapper mapper; private final GioHangService gioHangService; private final ThongBaoService thongBaoService; private final EmailService emailService; private final NhatKyHoatDongService nhatKyHoatDongService;
 public DonHangDto datHang(String maKh,DonHangDto.Checkout req){KhachHang kh=khachHangRepository.findById(maKh).orElseThrow();boolean muaNgay=req.maSanPham()!=null&&!req.maSanPham().isBlank();Map<String,Integer> muc=thanhToanService.mucMua(maKh,req.maSanPham(),req.soLuong());DiaChi dc=diaChiRepository.findById(req.maDiaChi()).filter(x->x.getMaKh().equals(maKh)).orElseThrow(()->new AppException(HttpStatus.BAD_REQUEST,"Địa chỉ giao hàng không hợp lệ"));DonHang d=new DonHang();d.setMaDonHang(IdGenerator.next("DH"));d.setKhachHang(kh);d.setNgayMua(LocalDateTime.now());d.setNgayCapNhat(d.getNgayMua());d.setTrangThai(TrangThaiDonHang.CHO_XAC_NHAN);d.setTenNguoiNhan(dc.getTenNguoiNhan());d.setSoDienThoaiNhan(dc.getSoDienThoaiNhan());d.setDiaChiGiaoHang(diaChiService.diaChiDayDu(dc));BigDecimal tam=BigDecimal.ZERO;for(var e:muc.entrySet()){PhienBanSanPham p=phienBanRepository.findById(e.getKey()).orElseThrow();if(p.getTrangThai()!=TrangThaiSanPham.DANG_KINH_DOANH||e.getValue()>p.getSoLuongTon())throw new AppException(HttpStatus.BAD_REQUEST,p.getSanPham().getTenMay()+" không đủ hàng");ChiTietDonHang c=new ChiTietDonHang();c.setMaChiTiet(IdGenerator.next("CTDH"));c.setDonHang(d);c.setPhienBan(p);c.setTenSanPham(p.getSanPham().getTenMay());c.setTenPhienBan(p.getRam()+"GB/"+p.getBoNho()+"GB/"+p.getMauSac());c.setSku(p.getSku());c.setHinhAnh(SanPhamMapper.anhTheoMau(p.getSanPham(),p));;c.setSoLuong(e.getValue());c.setDonGia(p.getGiaBan());c.setBaoHanhThang(p.getSanPham().getThoiHanBaoHanhThang());d.getChiTiet().add(c);tam=tam.add(p.getGiaBan().multiply(BigDecimal.valueOf(e.getValue())));}d.setTamTinh(tam);BigDecimal phi=phiVanChuyenService.tinhPhi(phiVanChuyenService.theoDiaChi(dc.getMaTinh()),tam);d.setPhiVanChuyen(phi);boolean moi=!donHangRepository.existsByKhachHangMaKhAndTrangThaiNot(maKh,TrangThaiDonHang.DA_HUY);if(coMa(req.maKm())){KhuyenMai k=dungVoucher(req.maKm(),LoaiKhuyenMai.DON_HANG,tam,moi);d.setKhuyenMai(k);d.setGiamGia(khuyenMaiService.tinhGiam(k,tam));}if(coMa(req.maKmVanChuyen())){KhuyenMai k=dungVoucher(req.maKmVanChuyen(),LoaiKhuyenMai.VAN_CHUYEN,tam,moi);d.setKhuyenMaiVanChuyen(k);d.setGiamPhiVanChuyen(khuyenMaiService.tinhGiam(k,phi));}d.setTongThanhToan(tam.subtract(d.getGiamGia()).add(phi).subtract(d.getGiamPhiVanChuyen()));DonHang luu=donHangRepository.save(d);doiTon(luu,-1,LoaiBienDongKho.BAN_HANG);taoThanhToan(luu,req.phuongThucTt());ghiTrangThai(luu,TrangThaiDonHang.CHO_XAC_NHAN,"Khách hàng đặt đơn");if(!muaNgay)gioHangRepository.findByKhachHangMaKh(maKh).ifPresent(g->g.getChiTiet().clear());DonHangDto dto=mapper.toDto(luu);thongBaoService.gui(maKh,"Đặt hàng thành công, đơn "+luu.getMaDonHang()+" đang chờ xác nhận");taiKhoanRepository.findById(maKh).ifPresent(tk->emailService.gui(tk.getEmail(),"Xác nhận đơn hàng "+luu.getMaDonHang(),"Cảm ơn bạn đã đặt hàng tại PhoneNest. Tổng thanh toán: "+dto.tongTien().toBigInteger()+" VND."));return dto;}
 public List<DonHangDto> lichSu(String maKh){return donHangRepository.findByKhachHangMaKhOrderByNgayMuaDesc(maKh).stream().map(mapper::toDto).toList();} public DonHangDto chiTiet(String maKh,String ma){return mapper.toDto(donCuaToi(maKh,ma));} public List<DonHangDto> tatCa(){return donHangRepository.findAllByOrderByNgayMuaDesc().stream().map(mapper::toDto).toList();} public DonHangDto chiTietQuanTri(String ma){return mapper.toDto(tim(ma));}
 public DonHangDto huy(String maKh,String ma,DonHangDto.HuyRequest req){DonHang d=donCuaToi(maKh,ma);if(d.getTrangThai()!=TrangThaiDonHang.CHO_XAC_NHAN&&d.getTrangThai()!=TrangThaiDonHang.DA_XAC_NHAN&&d.getTrangThai()!=TrangThaiDonHang.DANG_CHUAN_BI)throw new AppException(HttpStatus.BAD_REQUEST,"Đơn hàng đã bàn giao vận chuyển, không thể hủy");if(!LY_DO_HUY.contains(req.lyDo()))throw new AppException(HttpStatus.BAD_REQUEST,"Lý do hủy đơn không hợp lệ");if(LY_DO_KHAC.equals(req.lyDo())&&(req.ghiChu()==null||req.ghiChu().isBlank()))throw new AppException(HttpStatus.BAD_REQUEST,"Vui lòng nhập lý do hủy khác");doiTon(d,1,LoaiBienDongKho.HOAN_HUY_DON);d.setTrangThai(TrangThaiDonHang.DA_HUY);d.setNgayCapNhat(LocalDateTime.now());HuyDonHang h=new HuyDonHang();h.setMaDonHang(ma);h.setLyDo(req.lyDo());h.setGhiChu(req.ghiChu());h.setNgayHuy(LocalDateTime.now());huyRepository.save(h);ghiTrangThai(d,TrangThaiDonHang.DA_HUY,req.lyDo());return mapper.toDto(d);}
 public DonHangDto capNhatTrangThai(String ma,TrangThaiDonHang moi){
  DonHang d=timKhoa(ma);
  TrangThaiDonHang truoc=d.getTrangThai();
  if(truoc==TrangThaiDonHang.DA_GIAO||truoc==TrangThaiDonHang.DA_HUY)throw new AppException(HttpStatus.BAD_REQUEST,"Đơn hàng đã kết thúc");
  if(truoc==TrangThaiDonHang.CHO_KHACH_XAC_NHAN)throw new AppException(HttpStatus.CONFLICT,"Đơn đã giao, đang chờ khách hàng xác nhận nhận hàng");
  if(moi==TrangThaiDonHang.DA_GIAO)throw new AppException(HttpStatus.BAD_REQUEST,"Chỉ khách hàng mới xác nhận hoàn thành đơn hàng");
  if(moi==TrangThaiDonHang.CHO_KHACH_XAC_NHAN)throw new AppException(HttpStatus.BAD_REQUEST,"Vui lòng dùng chức năng Xác nhận đã giao hàng");
  if(moi==TrangThaiDonHang.DA_HUY)doiTon(d,1,LoaiBienDongKho.HOAN_HUY_DON);
  d.setTrangThai(moi);d.setNgayCapNhat(LocalDateTime.now());
  ghiTrangThai(d,moi,"Quản trị cập nhật trạng thái",SecurityUtil.currentUserId());
  auditTrangThai(ma,truoc,moi);
  thongBaoService.gui(d.getKhachHang().getMaKh(),"Đơn hàng "+ma+" chuyển sang trạng thái: "+moi.name());
  return mapper.toDto(d);
 }
 /** Admin xác nhận đã giao: DANG_GIAO -> CHO_KHACH_XAC_NHAN (chưa hoàn thành, chưa cộng điểm). */
 public DonHangDto danhDauDaGiao(String ma){
  DonHang d=timKhoa(ma);
  if(d.getTrangThai()!=TrangThaiDonHang.DANG_GIAO)throw new AppException(HttpStatus.CONFLICT,"Chỉ đơn đang giao mới được xác nhận đã giao hàng");
  LocalDateTime now=LocalDateTime.now();
  d.setTrangThai(TrangThaiDonHang.CHO_KHACH_XAC_NHAN);d.setNgayGiao(now);d.setNgayCapNhat(now);
  ghiTrangThai(d,TrangThaiDonHang.CHO_KHACH_XAC_NHAN,"Quản trị xác nhận đã giao hàng, chờ khách xác nhận",SecurityUtil.currentUserId());
  auditTrangThai(ma,TrangThaiDonHang.DANG_GIAO,TrangThaiDonHang.CHO_KHACH_XAC_NHAN);
  thongBaoService.gui(d.getKhachHang().getMaKh(),"Đơn hàng "+ma+" đã được giao. Vui lòng xác nhận khi bạn đã nhận được hàng.");
  return mapper.toDto(d);
 }

 /** Khách xác nhận đã nhận: CHO_KHACH_XAC_NHAN -> DA_GIAO (Hoàn thành), cộng điểm đúng một lần. */
 public DonHangDto khachXacNhanDaNhan(String maKh,String ma){
  DonHang d=timKhoa(ma);
  if(!d.getKhachHang().getMaKh().equals(maKh))throw new AppException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng");
  if(d.getTrangThai()!=TrangThaiDonHang.CHO_KHACH_XAC_NHAN)throw new AppException(HttpStatus.CONFLICT,"Đơn hàng không ở trạng thái chờ xác nhận nhận hàng hoặc đã được xác nhận trước đó");
  LocalDateTime now=LocalDateTime.now();
  d.setTrangThai(TrangThaiDonHang.DA_GIAO);d.setNgayKhachXacNhan(now);d.setNguoiXacNhan(maKh);d.setNgayCapNhat(now);
  congDiem(d);
  ghiTrangThai(d,TrangThaiDonHang.DA_GIAO,"Khách hàng xác nhận đã nhận hàng",maKh);
  auditTrangThai(ma,TrangThaiDonHang.CHO_KHACH_XAC_NHAN,TrangThaiDonHang.DA_GIAO);
  thongBaoService.gui(maKh,"Đơn hàng "+ma+" đã hoàn thành. Cảm ơn bạn đã mua sắm tại PhoneNest!");
  return mapper.toDto(d);
 }

 private void auditTrangThai(String ma,TrangThaiDonHang truoc,TrangThaiDonHang sau){
  Map<String,Object> t=new LinkedHashMap<>();t.put("trangThai",truoc.name());
  Map<String,Object> s=new LinkedHashMap<>();s.put("trangThai",sau.name());
  nhatKyHoatDongService.ghi(HanhDongAudit.CHANGE_ORDER_STATUS,LoaiDoiTuongAudit.ORDER,ma,t,s);
 }
 public GioHangDto muaLai(String maKh,String ma){List<GioHangDto.Request> ds=donCuaToi(maKh,ma).getChiTiet().stream().map(c->new GioHangDto.Request(c.getPhienBan().getMaPhienBan(),c.getSoLuong())).toList();return gioHangService.sync(maKh,ds);}
 public DonHangDto suaDong(String ma,String id,int sl){DonHang d=tim(ma);if(d.getTrangThai()!=TrangThaiDonHang.CHO_XAC_NHAN)throw new AppException(HttpStatus.BAD_REQUEST,"Chỉ sửa khi chờ xác nhận");Map<String,Object> duLieuTruoc=snapshotDong(d);ChiTietDonHang c=d.getChiTiet().stream().filter(x->x.getPhienBan().getMaPhienBan().equals(id)).findFirst().orElseThrow(()->new AppException(HttpStatus.NOT_FOUND,"Phiên bản không có trong đơn"));int delta=sl-c.getSoLuong();if(sl<=0){if(d.getChiTiet().size()==1)throw new AppException(HttpStatus.BAD_REQUEST,"Đơn phải còn ít nhất một sản phẩm");capTon(c.getPhienBan(),c.getSoLuong(),d,LoaiBienDongKho.DIEU_CHINH);d.getChiTiet().remove(c);}else{if(delta>c.getPhienBan().getSoLuongTon())throw new AppException(HttpStatus.BAD_REQUEST,"Không đủ tồn kho");capTon(c.getPhienBan(),-delta,d,LoaiBienDongKho.DIEU_CHINH);c.setSoLuong(sl);}recalc(d);nhatKyHoatDongService.ghi(HanhDongAudit.UPDATE_ORDER_ITEM,LoaiDoiTuongAudit.ORDER,ma,duLieuTruoc,snapshotDong(d));return mapper.toDto(d);}
 private Map<String,Object> snapshotDong(DonHang d){Map<String,Object> duLieu=new LinkedHashMap<>();List<Map<String,Object>> ds=new ArrayList<>();for(ChiTietDonHang c:d.getChiTiet()){Map<String,Object> m=new LinkedHashMap<>();m.put("maChiTiet",c.getMaChiTiet());m.put("maPhienBan",c.getPhienBan().getMaPhienBan());m.put("soLuong",c.getSoLuong());m.put("donGia",c.getDonGia());ds.add(m);}duLieu.put("items",ds);return duLieu;}
 private void recalc(DonHang d){BigDecimal tam=d.getChiTiet().stream().map(c->c.getDonGia().multiply(BigDecimal.valueOf(c.getSoLuong()))).reduce(BigDecimal.ZERO,BigDecimal::add);d.setTamTinh(tam);if(d.getKhuyenMai()!=null)d.setGiamGia(khuyenMaiService.tinhGiam(d.getKhuyenMai(),tam));d.setTongThanhToan(tam.subtract(d.getGiamGia()).add(d.getPhiVanChuyen()).subtract(d.getGiamPhiVanChuyen()));d.setNgayCapNhat(LocalDateTime.now());}
 private void taoThanhToan(DonHang d,PhuongThucTt pt){ThanhToan t=new ThanhToan();t.setMaThanhToan(IdGenerator.next("TT"));t.setDonHang(d);t.setPhuongThuc(pt);t.setSoTien(d.getTongThanhToan());t.setTrangThai(pt==PhuongThucTt.COD?TrangThaiThanhToan.CHO_THANH_TOAN:TrangThaiThanhToan.CHO_THANH_TOAN);t.setNgayTao(LocalDateTime.now());thanhToanRepository.save(t);}
 private void doiTon(DonHang d,int dau,LoaiBienDongKho loai){d.getChiTiet().forEach(c->capTon(c.getPhienBan(),dau*c.getSoLuong(),d,loai));} private void capTon(PhienBanSanPham p,int delta,DonHang d,LoaiBienDongKho loai){int truoc=p.getSoLuongTon(),sau=Math.max(0,truoc+delta);p.setSoLuongTon(sau);BienDongKho b=new BienDongKho();b.setMaBienDong(IdGenerator.next("BDK"));b.setPhienBan(p);b.setLoaiBienDong(loai);b.setSoLuong(delta);b.setTonTruoc(truoc);b.setTonSau(sau);b.setDonHang(d);b.setThoiGian(LocalDateTime.now());bienDongRepository.save(b);}

 private void ghiTrangThai(
         DonHang d,
         TrangThaiDonHang t,
         String gc) {

  LichSuTrangThaiDonHang l =
          new LichSuTrangThaiDonHang();

  l.setMaLichSu(IdGenerator.next("LSTT"));
  l.setDonHang(d);
  l.setTrangThai(t);
  l.setThoiGian(LocalDateTime.now());
  l.setGhiChu(gc);

  lichSuRepository.save(l);
 }

 private void ghiTrangThai(DonHang d,TrangThaiDonHang t,String gc,String maTk){LichSuTrangThaiDonHang l=new LichSuTrangThaiDonHang();l.setMaLichSu(IdGenerator.next("LSTT"));l.setDonHang(d);l.setTrangThai(t);l.setThoiGian(LocalDateTime.now());l.setGhiChu(gc);taiKhoanRepository.findById(maTk).ifPresent(l::setNguoiCapNhat);lichSuRepository.save(l);}
 private DonHang timKhoa(String ma){return donHangRepository.findByIdForUpdate(ma).orElseThrow(()->new AppException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng"));}
 private void congDiem(DonHang d){KhachHang k=d.getKhachHang();k.setDiemTichLuy(k.getDiemTichLuy()+d.getTamTinh().subtract(d.getGiamGia()).intValue()/DIEM_MOI_VND);k.setHangThanhVien(HangThanhVien.of(k.getDiemTichLuy()));}
 private DonHang tim(String ma){return donHangRepository.findById(ma).orElseThrow(()->new AppException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng"));} private DonHang donCuaToi(String maKh,String ma){return donHangRepository.findById(ma).filter(d->d.getKhachHang().getMaKh().equals(maKh)).orElseThrow(()->new AppException(HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng"));} private boolean coMa(String x){return x!=null&&!x.isBlank();}
 private KhuyenMai dungVoucher(String ma,LoaiKhuyenMai loai,BigDecimal tam,boolean moi){KhuyenMai k=khuyenMaiRepository.findById(ma.trim()).filter(x->x.getLoai()==loai).orElseThrow(()->new AppException(HttpStatus.BAD_REQUEST,"Mã khuyến mãi không hợp lệ"));String ly=khuyenMaiService.lyDoKhongDung(k,tam,moi);if(ly!=null)throw new AppException(HttpStatus.BAD_REQUEST,k.getMaKm()+": "+ly);k.setDaDung(k.getDaDung()+1);return k;}
}
