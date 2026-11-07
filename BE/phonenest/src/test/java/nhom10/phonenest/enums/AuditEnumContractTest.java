package nhom10.phonenest.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEnumContractTest {

    @Test
    @DisplayName("shouldContainExact24HanhDongAuditEnums")
    void shouldContainExact24HanhDongAuditEnums() {
        Set<String> actualNames = Arrays.stream(HanhDongAudit.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        Set<String> expectedNames = Set.of(
                "CREATE_PRODUCT",
                "UPDATE_PRODUCT",
                "DELETE_PRODUCT",

                "CREATE_CATEGORY",
                "UPDATE_CATEGORY",
                "DELETE_CATEGORY",

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
                .as("HanhDongAudit must match exactly the 24 planned actions")
                .containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(HanhDongAudit.values()).hasSize(24);
    }

    @Test
    @DisplayName("shouldContainExact10LoaiDoiTuongAuditEnums")
    void shouldContainExact10LoaiDoiTuongAuditEnums() {
        Set<String> actualNames = Arrays.stream(LoaiDoiTuongAudit.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        Set<String> expectedNames = Set.of(
                "PRODUCT",
                "CATEGORY",
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
                .as("LoaiDoiTuongAudit must match exactly the 10 planned entity types")
                .containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(LoaiDoiTuongAudit.values()).hasSize(10);
    }
}
