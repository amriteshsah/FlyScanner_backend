-- Optional traveler profile information; existing accounts remain valid.
ALTER TABLE account.users ADD COLUMN phone_number VARCHAR(25);
ALTER TABLE account.users ADD COLUMN date_of_birth DATE;
ALTER TABLE account.users ADD COLUMN nationality VARCHAR(2);
ALTER TABLE account.users ADD COLUMN home_airport VARCHAR(3);
ALTER TABLE account.users ADD COLUMN preferred_currency VARCHAR(3);
