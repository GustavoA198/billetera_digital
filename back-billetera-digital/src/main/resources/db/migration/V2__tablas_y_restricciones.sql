-- ENUMS
CREATE TYPE wallet_status AS ENUM (
    'ACTIVE',
    'BLOCKED'
);

CREATE TYPE operation_type AS ENUM (
    'DEPOSIT',
    'WITHDRAWAL',
    'TRANSFER'
);

CREATE TYPE operation_status AS ENUM (
    'REJECTED',
    'COMPLETED'
);

CREATE TYPE ledger_entry_direction AS ENUM (
    'CREDIT',
    'DEBIT'
);

CREATE TYPE app_user_role AS ENUM (
    'CUSTOMER',
    'TELLER',
    'ADMIN'
);

-- TABLES

CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role app_user_role NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE wallet (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id uuid NOT NULL UNIQUE CONSTRAINT fk_wallet_user REFERENCES app_user(id),
    balance NUMERIC(18,2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    status wallet_status NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE operation (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    type operation_type NOT NULL,
    source_wallet_id uuid CONSTRAINT fk_operation_source_wallet REFERENCES wallet(id),
    target_wallet_id uuid CONSTRAINT fk_operation_target_wallet REFERENCES wallet(id),
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    status operation_status NOT NULL,
    rejection_code VARCHAR(50),
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(255) NOT NULL,
    initiated_by uuid NOT NULL CONSTRAINT fk_operation_initiated_by REFERENCES app_user(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT operation_initiated_by_idempotency_key_unique UNIQUE (initiated_by, idempotency_key),
    CONSTRAINT operation_wallets_by_type_check CHECK (
        (type = 'TRANSFER'   AND source_wallet_id IS NOT NULL AND target_wallet_id IS NOT NULL)
     OR (type = 'DEPOSIT'    AND source_wallet_id IS NULL     AND target_wallet_id IS NOT NULL)
     OR (type = 'WITHDRAWAL' AND source_wallet_id IS NOT NULL AND target_wallet_id IS NULL)
    ),
    CONSTRAINT operation_rejection_code_by_status CHECK (
        (status = 'REJECTED' AND rejection_code IS NOT NULL)
     OR (status = 'COMPLETED' AND rejection_code IS NULL)
    )
);

CREATE TABLE ledger_entry (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    operation_id uuid NOT NULL CONSTRAINT fk_ledger_entry_operation REFERENCES operation(id),
    wallet_id uuid NOT NULL CONSTRAINT fk_ledger_entry_wallet REFERENCES wallet(id),
    direction ledger_entry_direction NOT NULL,
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    balance_after NUMERIC(18,2) NOT NULL CHECK (balance_after >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_token (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id uuid NOT NULL CONSTRAINT fk_refresh_token_user REFERENCES app_user(id),
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by uuid CONSTRAINT fk_refresh_token_replaced_by REFERENCES refresh_token(id)
);

CREATE TABLE audit_log (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    table_name VARCHAR(255) NOT NULL,
    record_id uuid NOT NULL,
    action VARCHAR(255) NOT NULL,
    old_value VARCHAR(255),
    new_value VARCHAR(255),
    changed_by uuid NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
