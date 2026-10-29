package nhom10.phonenest.service;
import lombok.RequiredArgsConstructor; import nhom10.phonenest.dto.GioHangDto; import nhom10.phonenest.entity.*; import nhom10.phonenest.enums.TrangThaiSanPham; import nhom10.phonenest.exception.AppException; import nhom10.phonenest.mapper.SanPhamMapper; import nhom10.phonenest.repository.*; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor @Transactional public class GioHangService{
 private final GioHangRepository gioHangRepository; private final PhienBanSanPhamRepository phienBanRepository;
 public GioHangDto get(String maKh){return toDto(gioHang(maKh));}
 public GioHangDto add(String maKh,String ma,int sl){GioHang g=gioHang(maKh); PhienBanSanPham p=resolve(ma); ChiTietGioHang c=find(g,p.getMaPhienBan()); return set(g,p,(c==null?0:c.getSoLuong())+sl);}
 public GioHangDto update(String maKh,String ma,int sl){GioHang g=gioHang(maKh); PhienBanSanPham p=resolve(ma); ChiTietGioHang c=find(g,p.getMaPhienBan()); if(c==null) return sl<=0?toDto(g):set(g,p,sl); if(sl<=0)g.getChiTiet().remove(c);else validate(p,sl); if(sl>0)c.setSoLuong(sl); touch(g); return toDto(g);}
 public GioHangDto remove(String maKh,String ma){GioHang g=gioHang(maKh); String id=resolve(ma).getMaPhienBan(); g.getChiTiet().removeIf(c->c.getPhienBan().getMaPhienBan().equals(id)); touch(g); return toDto(g);}
 public GioHangDto clear(String maKh){GioHang g=gioHang(maKh);g.getChiTiet().clear();touch(g);return toDto(g);}
 public GioHangDto sync(String maKh,List<GioHangDto.Request> ds){GioHang g=gioHang(maKh); for(var r:ds)try{PhienBanSanPham p=resolve(r.maSanPham());ChiTietGioHang c=find(g,p.getMaPhienBan());int n=(c==null?0:c.getSoLuong())+r.soLuong();validate(p,n); if(c==null)addLine(g,p,n);else c.setSoLuong(n);}catch(AppException ignored){} touch(g);return toDto(g);}
 private GioHangDto set(GioHang g,PhienBanSanPham p,int sl){validate(p,sl);ChiTietGioHang c=find(g,p.getMaPhienBan());if(c==null)addLine(g,p,sl);else c.setSoLuong(sl);touch(g);return toDto(g);}
 private void addLine(GioHang g,PhienBanSanPham p,int sl){ChiTietGioHang c=new ChiTietGioHang();c.setId(new ChiTietGioHang.Id(g.getMaGioHang(),p.getMaPhienBan()));c.setGioHang(g);c.setPhienBan(p);c.setSoLuong(sl);g.getChiTiet().add(c);}
 private void validate(PhienBanSanPham p,int sl){if(p.getTrangThai()!=TrangThaiSanPham.DANG_KINH_DOANH||sl<1||sl>p.getSoLuongTon())throw new AppException(HttpStatus.BAD_REQUEST,"Số lượng không hợp lệ (còn "+p.getSoLuongTon()+" sản phẩm)");}
 private PhienBanSanPham resolve(String ma){return phienBanRepository.findById(ma).orElseThrow(()->new AppException(HttpStatus.NOT_FOUND,"Phiên bản sản phẩm không tồn tại"));}
 private ChiTietGioHang find(GioHang g,String id){return g.getChiTiet().stream().filter(c->c.getPhienBan().getMaPhienBan().equals(id)).findFirst().orElse(null);}
 private GioHang gioHang(String maKh){return gioHangRepository.findByKhachHangMaKh(maKh).orElseThrow(()->new AppException(HttpStatus.NOT_FOUND,"Không tìm thấy giỏ hàng"));}
 private void touch(GioHang g){g.setNgayCapNhat(LocalDateTime.now());}
 private nhom10.phonenest.dto.SanPhamDto.PhienBan toPhienBanDto(PhienBanSanPham p){return new nhom10.phonenest.dto.SanPhamDto.PhienBan(p.getMaPhienBan(),p.getSku(),p.getRam(),p.getBoNho(),p.getMauSac(),p.getGiaBan(),p.getGiaNiemYet(),p.getSoLuongTon(),p.getTrangThai());}
 private GioHangDto toDto(GioHang g){List<GioHangDto.Item> items=g.getChiTiet().stream().map(c->new GioHangDto.Item(SanPhamMapper.toDto(c.getPhienBan().getSanPham()),toPhienBanDto(c.getPhienBan()),c.getSoLuong())).toList();BigDecimal tong=g.getChiTiet().stream().map(c->c.getPhienBan().getGiaBan().multiply(BigDecimal.valueOf(c.getSoLuong()))).reduce(BigDecimal.ZERO,BigDecimal::add);return new GioHangDto(items,tong);}
}
