package dev.moraveis.billingservice.customer;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponse findById(UUID id) {
        return CustomerResponse.from(findOrThrowException(id));
    }

    private @NonNull Customer findOrThrowException(UUID id) {
        return customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException("Customer with id " + id + " not found"));
    }

    public Page<CustomerResponse> findAll(Pageable pageable) {
        return customerRepository.findAll(pageable).map(CustomerResponse::from);
    }

    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        try {
            return CustomerResponse.from(customerRepository.saveAndFlush(new Customer(request.name(), request.email(), request.currency())));
        } catch (DataIntegrityViolationException ex) {
            if (isEmailConstraint(ex)) {
                throw new EmailAlreadyExistsException(ex);
            }
            throw ex;
        }
    }

    private boolean isEmailConstraint(DataIntegrityViolationException ex) {
        if (!(ex.getMostSpecificCause() instanceof ConstraintViolationException cve)) return false;
        return Objects.nonNull(cve.getConstraintName())
                && cve.getConstraintName().contains("SQLState 23505")
                && "idx_customer_email_lower".equals(cve.getConstraintName());
    }

}
