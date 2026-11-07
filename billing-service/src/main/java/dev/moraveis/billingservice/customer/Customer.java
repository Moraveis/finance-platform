package dev.moraveis.billingservice.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7) // Style.TIME generates UUIDv7 in Hibernate 6.5+
    private UUID id;

    @Setter
    @Column(nullable = false, length = 256)
    private String name;

    @Setter
    @Column(nullable = false, length = 256)
    private String email;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Customer(String name, String email, String currency) {
        this.name = name;
        this.email = email;
        this.currency = currency;
    }
}
