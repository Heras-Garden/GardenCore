# How GardenCore Works

GardenCore starts the configured database pool, ensures the shared schema exists, and registers service interfaces with Bukkit's services manager.

Other Garden plugins do not open their own economy or integration connections. They request the GardenCore services they need. This keeps balances, orders, organizations, and Iris integration on one shared database.

## Economy

GardenCore is the canonical Obol balance provider. The Vault provider points other plugins at the same balance service.

## Orders

Payments that need rollback or duplicate protection are recorded as orders with explicit states. GardenTrade, GardenEvents, GardenPost, and other modules use this journal for durable transactions.

## Iris

Minecraft-to-Iris events are written to the integration outbox. Iris reads and processes those rows, then marks them processed. Commands from Iris use the integration inbox.

## Startup order

1. Vault
2. GardenCore
3. GardenLands and other Garden plugins
4. Iris may start after GardenCore has created the shared schema.