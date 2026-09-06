# Llamination

RTS game inspired by Ages of Empires and Command & Conquer with a twist that each faction
is cooperatively controlled, turning a 1v1 to a (max) 4v4. Each team of (up to) 4 must
coordinate amongst each other, sharing resources and units.

## Running locally

Start the application from the backend directory:

```sh
cd backend
./gradlew bootRun
```

Then open <http://localhost:8080>. The included development login is
`commander` / `llama`.

Accounts are read from `backend/config/users.txt` in `username:password` format.
Blank lines and lines beginning with `#` are ignored. Set `LLAMINATION_USERS_FILE`
to point at another credentials file. The file is re-read on each login, so account
changes do not require restarting the server.

This deliberately simple credential store uses plain-text passwords and is intended
for local development only. A deployed service should use salted password hashes and
a managed account store.
