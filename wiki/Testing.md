# Testing

Run these checks after every GardenCore update.

1. Confirm GardenCore enables without a database error.
2. Run `/balance` and `/obol balance` and confirm they agree.
3. Use `/pay` between two test players and confirm the amount moves exactly once.
4. Restart the server and confirm balances persist.
5. Create one Garden action that writes an order and confirm it is not duplicated after restart.
6. If Iris is enabled, submit one integration event and confirm it is processed once.

Do not use `/reload` for Garden plugin testing.