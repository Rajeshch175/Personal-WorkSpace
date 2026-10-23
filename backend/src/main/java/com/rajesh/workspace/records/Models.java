package com.rajesh.workspace.records;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public final class Models {
    private Models() {}
    public record TimeBlock(@NotBlank @Size(max=120) String title, @NotNull LocalDate date,
        @NotNull LocalTime time, @Min(1) @Max(1440) int minutes,
        @NotNull @Pattern(regexp="work|learning|course|wellbeing") String type,
        @NotNull @Pattern(regexp="planned|active|done") String status) {}
    public record Course(@NotBlank @Size(max=120) String name, @NotNull @Size(max=240) String subtitle,
        @Min(1) @Max(100000) int total, @Min(0) int completed, @Size(max=2000) String url) {}
    public record Finance(@NotBlank @Size(max=120) String name, @NotNull LocalDate date,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
        @NotNull @Pattern(regexp="investment|bill|subscription") String category,
        @NotNull @Pattern(regexp="scheduled|paid") String status, @Pattern(regexp="none|monthly|yearly") String recurrence) {}
    public record Loan(@NotBlank @Size(max=120) String name, @NotNull @Size(max=240) String note,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,
        @NotNull LocalDate dueDate) {}
    public record Payment(@NotNull LocalDate dueDate) {}
    public record Repayment(@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount) {}
    public record Habit(@NotBlank @Size(max=120) String name) {}
    public record Settings(@NotBlank @Size(max=80) String name, @NotNull @Size(max=240) String intention) {}
}
