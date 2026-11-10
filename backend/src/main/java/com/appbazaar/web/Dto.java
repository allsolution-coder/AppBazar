package com.appbazaar.web;

import com.appbazaar.model.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Dto {
    private Dto() {}

    public record RegisterReq(@NotBlank String name, @Email @NotBlank String email,
                              @Size(min = 8, message = "Password must be at least 8 characters") String password,
                              String role) {}
    public record CodeReq(@Email @NotBlank String email) {}
    public record VerifyReq(@Email @NotBlank String email, @NotBlank @Size(min = 6, max = 6) String code, String role) {}
    public record SignupReq(@Email @NotBlank String email,
                            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
                            String role) {}
    public record ResetReq(@Email @NotBlank String email, @NotBlank @Size(min = 6, max = 6) String code,
                           @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {}
    public record LoginReq(@NotBlank String email, @NotBlank String password) {}
    public record AuthRes(String token, String name, String role, boolean feePaid) {}

    public record ProductRes(Long id, String name, String category, int price, String description,
                             double rating, int reviewCount, String status, String seller,
                             boolean hasFile, String fileName, Long fileSize,
                             String techStack, String college) {
        public static ProductRes of(Product p) {
            return new ProductRes(p.getId(), p.getName(), p.getCategory(), p.getPrice(),
                    p.getDescription(), p.getRating(),
                    p.getReviewCount() == null ? 0 : p.getReviewCount(), p.getStatus().name(), p.getSeller().getName(),
                    p.getStoredFileName() != null, p.getFileName(), p.getFileSize(),
                    p.getTechStack(), p.getCollege());
        }
    }

    public record CheckoutReq(@NotEmpty List<Long> productIds) {}
    public record ItemRes(Long id, String name, boolean hasFile, String fileName) {}
    public record OrderRes(Long id, List<ItemRes> items, int total, Instant createdAt, String status) {
        public static OrderRes of(Orders o) {
            return new OrderRes(o.getId(),
                    o.getItems().stream().map(p -> new ItemRes(p.getId(), p.getName(),
                            p.getStoredFileName() != null, p.getFileName())).toList(),
                    o.getTotal(), o.getCreatedAt(), "PAID");
        }
    }
    public record ReviewReq(@Min(1) @Max(5) int rating, @NotBlank @Size(max = 1000) String comment) {}
    public record ReviewRes(Long id, String user, int rating, String comment, Instant createdAt) {
        public static ReviewRes of(Review r) {
            return new ReviewRes(r.getId(), r.getUser().getName(), r.getRating(), r.getComment(), r.getCreatedAt());
        }
    }
    public record ConfirmReq(@NotBlank String paymentId) {}
    public record ConfirmRes(String kind, AuthRes auth, OrderRes order) {}
    public record AccountRes(String name, String email, String role, boolean feePaid, Instant feePaidAt,
                             long orders, long listings) {}
    public record NameReq(@NotBlank @Size(max = 60) String name) {}
    public record PasswordReq(@NotBlank String currentPassword,
                              @Size(min = 8, max = 72, message = "New password must be 8 to 72 characters") String newPassword) {}
    public record Stats(long liveProducts, long pending, long orders, long revenue, long commission, long feeIncome) {}
}
