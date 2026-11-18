-- Store only an HMAC of the short-lived email login code.
CREATE TABLE account.email_login_otps (
    user_id UUID PRIMARY KEY,
    otp_hash CHAR(64) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_email_login_otp_user FOREIGN KEY (user_id)
        REFERENCES account.users (id) ON DELETE CASCADE,
    CONSTRAINT ck_email_login_otp_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_email_login_otp_expiry CHECK (expires_at > sent_at)
);

CREATE INDEX idx_email_login_otp_expiry ON account.email_login_otps (expires_at);
