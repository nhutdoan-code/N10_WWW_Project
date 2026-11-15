package nhom10.phonenest.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEnumContractTest {

    @Test
    @DisplayName("shouldContainExact29HanhDongAuditEnums")
    void shouldContainExact24HanhDongAuditEnums() {
        Set<String> actualNames = Arrays.stream(HanhDongAudit.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        Set<String> expectedNames = Set.of(
                "CREATE_PRODUCT",
                "UPDATE_PRODUCT",
                "DELETE_PRODUCT",

                // 2B.7B: phe duyet ban hang bien the
                "APPROVE_VARIANT",
                "REVOKE_VARIANT_APPROVAL",
                "BLOCK_VARIANT",
                "UNBLOCK_VARIANT",
                "INVALIDATE_VARIANT_APPROVAL",

                "CREATE_CATEGORY",
                "UPDATE_CATEGORY",
                "DELETE_CATEGORY",

                // PR #24: quan ly thuong hieu va tao tai khoan quan tri
                "CREATE_BRAND",
                "UPDATE_BRAND",
                "CREATE_ACCOUNT",

                "STOCK_IMPORT",
                "STOCK_ADJUSTMENT",

                "CHANGE_ORDER_STATUS",
                "UPDATE_ORDER_ITEM",

                "LOCK_ACCOUNT",
                "UNLOCK_ACCOUNT",
                "UPDATE_ACCOUNT",
                "DELETE_ACCOUNT",

                "CREATE_PROMOTION",
                "UPDATE_PROMOTION",
                "DELETE_PROMOTION",

                "CREATE_SHIPPING_FEE",
                "UPDATE_SHIPPING_FEE",
                "DELETE_SHIPPING_FEE",

                "ANSWER_QUESTION",
                "DELETE_QUESTION",

                "DELETE_REVIEW",

                "CHANGE_RETURN_STATUS"
        );

        assertThat(actualNames)
                .as("HanhDongAudit must match exactly the 32 planned actions")
                .containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(HanhDongAudit.values()).hasSize(32);
    }

    @Test
    @DisplayName("shouldContainExact11LoaiDoiTuongAuditEnums")
    void shouldContainExact10LoaiDoiTuongAuditEnums() {
        Set<String> actualNames = Arrays.stream(LoaiDoiTuongAudit.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        Set<String> expectedNames = Set.of(
                "PRODUCT",
                "VARIANT",
                "CATEGORY",
                "BRAND",
                "INVENTORY",
                "ORDER",
                "ACCOUNT",
                "PROMOTION",
                "SHIPPING_FEE",
                "QUESTION",
                "REVIEW",
                "RETURN"
        );

        assertThat(actualNames)
                .as("LoaiDoiTuongAudit must match exactly the 12 planned entity types")
                .containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(LoaiDoiTuongAudit.values()).hasSize(12);
    }
}
