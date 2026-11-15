-- Optional traveler profile information; existing accounts remain valid.
ALTER TABLE account.users ADD COLUMN phone_number VARCHAR(25);
ALTER TABLE account.users ADD COLUMN date_of_birth DATE;
ALTER TABLE account.users ADD COLUMN nationality CHAR(2);
ALTER TABLE account.users ADD COLUMN home_airport CHAR(3);
ALTER TABLE account.users ADD COLUMN preferred_currency CHAR(3);
