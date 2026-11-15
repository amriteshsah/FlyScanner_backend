package com.flyscanner.account.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountRepository {

    private static final String ACCOUNT_COLUMNS = """
            id, email, full_name, password_hash, role, created_at,
            phone_number, date_of_birth, nationality, home_airport, preferred_currency
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID id, String email, String fullName, String passwordHash, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO account.users (
                    id, external_subject, email, full_name, user_status,
                    created_at, updated_at, password_hash
                ) VALUES (?, NULL, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                id, email, fullName, Timestamp.from(now), Timestamp.from(now), passwordHash
        );
    }

    public Optional<AccountRecord> findByEmail(String email) {
        return jdbcTemplate.query(
                "SELECT " + ACCOUNT_COLUMNS + " FROM account.users WHERE email = ? AND user_status = 'ACTIVE'",
                (rs, rowNum) -> mapAccount(rs),
                email
        ).stream().findFirst();
    }

    public Optional<AccountRecord> findById(UUID id) {
        return jdbcTemplate.query(
                "SELECT " + ACCOUNT_COLUMNS + " FROM account.users WHERE id = ? AND user_status = 'ACTIVE'",
                (rs, rowNum) -> mapAccount(rs),
                id
        ).stream().findFirst();
    }

    public boolean updateProfile(
            UUID id,
            String fullName,
            String phoneNumber,
            LocalDate dateOfBirth,
            String nationality,
            String homeAirport,
            String preferredCurrency,
            Instant updatedAt) {

        return jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    UPDATE account.users
                    SET full_name = ?, phone_number = ?, date_of_birth = ?, nationality = ?,
                        home_airport = ?, preferred_currency = ?, updated_at = ?
                    WHERE id = ? AND user_status = 'ACTIVE'
                    """);
            statement.setString(1, fullName);
            statement.setString(2, phoneNumber);
            if (dateOfBirth == null) {
                statement.setNull(3, Types.DATE);
            } else {
                statement.setDate(3, Date.valueOf(dateOfBirth));
            }
            statement.setString(4, nationality);
            statement.setString(5, homeAirport);
            statement.setString(6, preferredCurrency);
            statement.setTimestamp(7, Timestamp.from(updatedAt));
            statement.setObject(8, id);
            return statement;
        }) == 1;
    }

    private AccountRecord mapAccount(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date dateOfBirth = rs.getDate("date_of_birth");
        return new AccountRecord(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("phone_number"),
                dateOfBirth == null ? null : dateOfBirth.toLocalDate(),
                rs.getString("nationality"),
                rs.getString("home_airport"),
                rs.getString("preferred_currency")
        );
    }
}
