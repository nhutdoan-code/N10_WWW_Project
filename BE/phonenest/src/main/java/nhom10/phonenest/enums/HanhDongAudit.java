package nhom10.phonenest.enums;

/**
 * Cac thao tac quan tri duoc ghi vao NhatKyHoatDong.
 * Chi them gia tri khi co nghiep vu tuong ung trong he thong.
 */
public enum HanhDongAudit {

    CREATE_PRODUCT,
    UPDATE_PRODUCT,
    DELETE_PRODUCT,

    CREATE_CATEGORY,
    UPDATE_CATEGORY,
    DELETE_CATEGORY,

    STOCK_IMPORT,
    STOCK_ADJUSTMENT,

    CHANGE_ORDER_STATUS,
    UPDATE_ORDER_ITEM,

    LOCK_ACCOUNT,
    UNLOCK_ACCOUNT,
    UPDATE_ACCOUNT,
    DELETE_ACCOUNT,

    CREATE_PROMOTION,
    UPDATE_PROMOTION,
    DELETE_PROMOTION,

    CREATE_SHIPPING_FEE,
    UPDATE_SHIPPING_FEE,
    DELETE_SHIPPING_FEE,

    ANSWER_QUESTION,
    DELETE_QUESTION,

    DELETE_REVIEW,

    CHANGE_RETURN_STATUS
}
