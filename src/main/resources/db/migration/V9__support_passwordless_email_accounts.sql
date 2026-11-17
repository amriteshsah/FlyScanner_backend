-- Challenges are keyed by normalized email so users can verify before an account exists.
DROP TABLE account.email_login_otps;

CREATE TABLE account.email_login_otps (
    email VARCHAR(320) PRIMARY KEY,
    otp_hash CHAR(64) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_email_login_otp_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_email_login_otp_expiry CHECK (expires_at > sent_at)
);

-- Stable rows serialize initial requests/resends even before a challenge exists.
CREATE TABLE account.email_login_otp_locks (
    email VARCHAR(320) PRIMARY KEY
);

-- OTP-created accounts do not have a password until a future controlled Admin provisioning flow.
ALTER TABLE account.users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE account.users ADD COLUMN profile_completed BOOLEAN NOT NULL DEFAULT TRUE;
