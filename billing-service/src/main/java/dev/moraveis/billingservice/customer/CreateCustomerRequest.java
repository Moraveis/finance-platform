package dev.moraveis.billingservice.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(@NotBlank @Size(max = 256) String name,
                                    @NotBlank @Email @Size(max = 256) String email,
                                    @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) {
}
