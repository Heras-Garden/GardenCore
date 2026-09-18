# Configuration

The most important section is `database`.

For Iris integration, use MariaDB and point both Minecraft and Iris at the same database.

```yaml
database:
  type: mariadb
  mariadb:
    host: database-host
    port: 3306
    database: gardendb
    username: garden
    password: "change-me"
    ssl: false
```

After a one-time migration from an older Vault economy, set `obols.migrate-existing-vault-provider` to `false`.

Do not commit database passwords to GitHub.