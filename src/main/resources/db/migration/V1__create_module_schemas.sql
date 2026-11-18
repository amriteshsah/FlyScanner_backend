-- Establish module ownership boundaries before creating domain tables.
-- Each module will own its tables and migrations.
CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS search;
CREATE SCHEMA IF NOT EXISTS affiliate;
CREATE SCHEMA IF NOT EXISTS alerts;
CREATE SCHEMA IF NOT EXISTS notifications;
