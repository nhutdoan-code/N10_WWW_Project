package nhom10.phonenest.dto;

public record AdminAfterSalesSummaryDto(
        long waiting,
        long processing,
        long open,
        long overdue,
        long completedToday,
        long total) {}
