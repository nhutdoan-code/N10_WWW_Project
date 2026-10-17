package nhom10.phonenest.service;

import nhom10.phonenest.dto.DiaChiDto;
import nhom10.phonenest.entity.DiaChi;
import nhom10.phonenest.exception.AppException;
import nhom10.phonenest.repository.DiaChiRepository;
import nhom10.phonenest.util.IdGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DiaChiService {

    private final DiaChiRepository diaChiRepository;

    @Transactional(readOnly = true)
    public List<DiaChiDto> list(String maKh) {
        return diaChiRepository.findByMaKhOrderByMacDinhDescMaDiaChiAsc(maKh).stream().map(this::toDto).toList();
    }

    public DiaChiDto save(String maKh, String ma, DiaChiDto dto) {
        boolean dauTien = ma == null && diaChiRepository.findByMaKhOrderByMacDinhDescMaDiaChiAsc(maKh).isEmpty();
        DiaChi d = ma == null ? new DiaChi() : cuaToi(maKh, ma);
        if (ma == null) {
            d.setMaDiaChi(IdGenerator.next("DC"));
            d.setMaKh(maKh);
        }
        d.setTenNguoiNhan(dto.tenNguoiNhan().trim());
        d.setSoDienThoaiNhan(dto.soDienThoaiNhan());
        d.setDiaChiNhanHang(dto.diaChiNhanHang().trim());
        d.setTinhThanhPho(dto.tinhThanhPho());
        if (dto.macDinh() || d.isMacDinh() || dauTien) {
            boMacDinh(maKh);
            d.setMacDinh(true);
        }
        return toDto(diaChiRepository.save(d));
    }

    public void delete(String maKh, String ma) {
        DiaChi d = cuaToi(maKh, ma);
        diaChiRepository.delete(d);
        diaChiRepository.flush();
        if (d.isMacDinh()) {
            diaChiRepository.findByMaKhOrderByMacDinhDescMaDiaChiAsc(maKh).stream().findFirst()
                    .ifPresent(x -> x.setMacDinh(true));
        }
    }

    public DiaChiDto datMacDinh(String maKh, String ma) {
        DiaChi d = cuaToi(maKh, ma);
        boMacDinh(maKh);
        d.setMacDinh(true);
        return toDto(d);
    }

    private void boMacDinh(String maKh) {
        diaChiRepository.findByMaKhOrderByMacDinhDescMaDiaChiAsc(maKh).forEach(x -> x.setMacDinh(false));
    }

    private DiaChi cuaToi(String maKh, String ma) {
        return diaChiRepository.findById(ma)
                .filter(d -> d.getMaKh().equals(maKh))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ"));
    }

    private DiaChiDto toDto(DiaChi d) {
        return new DiaChiDto(d.getMaDiaChi(), d.getTenNguoiNhan(), d.getSoDienThoaiNhan(), d.getDiaChiNhanHang(),
                d.getTinhThanhPho(), d.isMacDinh());
    }
}
