package nhom10.phonenest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import nhom10.phonenest.enums.LoaiHanhVi;

public record TrackingDto() {

    public record Event(
            @NotBlank
            @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            String visitorId,
            @NotNull LoaiHanhVi type,
            @Size(max = 50) String productId,
            @Size(max = 120) String searchTerm,
            @Pattern(regexp = "HOME|PRODUCT_LIST|PRODUCT_DETAIL|CART|COMPARE|LOGIN|REGISTER")
            String pageType) {
    }
}
