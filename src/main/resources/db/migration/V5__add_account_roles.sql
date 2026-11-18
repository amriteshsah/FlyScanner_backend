-- Persist a server-controlled role for authorization.
-- All existing and newly registered users default to USER; clients cannot set this field.
ALTER TABLE account.users
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'USER';

ALTER TABLE account.users
    ADD CONSTRAINT ck_account_users_role CHECK (role IN ('USER', 'ADMIN'));
