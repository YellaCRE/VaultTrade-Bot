# VaultTrade Bot

Monorepo for the VaultTrade trading server and the future management UI.

## Structure

```text
.
|-- server/   # Spring Boot trading API and execution engine
|-- admin/    # React-based management UI
|-- docs/     # Project notes and design docs
`-- .github/  # CI workflows
```

## Server

Run the backend from [server](C:/Users/HCJ/IdeaProjects/VaultTrade-Bot/server):

```powershell
cd server
.\gradlew.bat bootRun
```

Local test mode:

```powershell
cd server
.\gradlew.bat bootRun --args="--spring.profiles.active=local-test"
```

## Admin

The management UI scaffold lives in [admin](C:/Users/HCJ/IdeaProjects/VaultTrade-Bot/admin).

Suggested startup flow after dependencies are installed:

```powershell
cd admin
npm install
npm run dev
```
