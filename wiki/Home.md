# GardenCore Wiki

GardenCore is the shared platform plugin for The Garden SMP. It owns the database connection, Obol economy, shared claim storage, organizations, orders, and integration queues used by the other Garden plugins.

## Responsibilities

- MariaDB and SQLite storage
- Obol balances and Vault economy provider
- Shared claim and property persistence
- Organization data
- Order and transaction journal
- Integration inbox and outbox for Iris
- Core APIs consumed by the rest of the Garden plugin family

## Dependencies

- Paper 26.2
- Java 25
- Vault or VaultUnlocked

GardenCore should load before every other Garden plugin.