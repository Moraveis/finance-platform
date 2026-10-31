CREATE TABLE customer
(
    id         UUID PRIMARY KEY                  DEFAULT uuidv7(),
    name       VARCHAR(256)             NOT NULL,
    email      VARCHAR(256)             NOT NULL,
    currency   VARCHAR(3)               NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL

);

CREATE TABLE plan
(
    id             UUID PRIMARY KEY                  DEFAULT uuidv7(),
    name           VARCHAR(100)             NOT NULL,
    price_amount   NUMERIC(19, 4)           NOT NULL CHECK (price_amount >= 0),
    currency       VARCHAR(3)               NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    billing_period VARCHAR(10)              NOT NULL CHECK (billing_period IN ('MONTHLY', 'YEARLY')),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE subscription
(
    id                 UUID PRIMARY KEY                  DEFAULT uuidv7(),
    status             VARCHAR(10)              NOT NULL CHECK (status IN ('ACTIVE', 'CANCELED')),
    start_date         DATE                     NOT NULL,
    current_period_end DATE                     NOT NULL,
    customer_id        UUID                     NOT NULL,
    plan_id            UUID                     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_customer_id FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_plan_id FOREIGN KEY (plan_id) REFERENCES plan (id),
    CONSTRAINT ck_subscription_period_valid CHECK (current_period_end > start_date)
);

CREATE TABLE invoice
(
    id              UUID PRIMARY KEY                  DEFAULT uuidv7(),
    amount          NUMERIC(19, 4)           NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3)               NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status          VARCHAR(10)              NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'PAID', 'VOID')),
    issued_at       TIMESTAMP WITH TIME ZONE,
    due_date        DATE                     NOT NULL,
    subscription_id UUID                     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_subscription_id FOREIGN KEY (subscription_id) REFERENCES subscription (id),
    CONSTRAINT ck_invoice_issued_at_matches_status CHECK ((status = 'DRAFT') = (issued_at IS NULL))
);

CREATE UNIQUE INDEX idx_customer_email_lower ON customer (lower(email));
CREATE INDEX idx_subscription_customer_id ON subscription (customer_id);
CREATE INDEX idx_subscription_plan_id ON subscription (plan_id);
CREATE INDEX idx_invoice_subscription_id ON invoice (subscription_id);
