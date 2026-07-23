# TokEngine — Cross-DLT Token Exchange

Canonical agent instructions for this repository. Tool-agnostic — `CLAUDE.md` is
just a pointer to this file. The workspace-level `../AGENTS.md` still applies;
anything here overrides it within this repo.

TokEngine is an atomic state machine that verifies incoming payments on one DLT
and produces outgoing transactions on another. Virtual credit is held in a
Convex lattice (merkle tree) structure so the engine never persists an
inconsistent state, regardless of external failure. Assets, accounts and chains
are identified using the CAIP standards (CAIP-2 / CAIP-10 / CAIP-19).

User-facing docs live in `README.md` (concepts, config, API) and
`WALKTHROUGH.md` (end-to-end Sepolia → Convex worked example).

## Repository layout

```
tokengine/                       # world.convex:tokengine — single Maven module
├── src/main/java/tokengine/
│   ├── TokengineMain.java       #   Entry point: load config, configure logback, launch
│   ├── Engine.java              #   Core: lattice state, credits, deposits, payouts
│   ├── Fields.java              #   Interned AString field-name constants
│   ├── APIServer.java           #   Javalin server setup (CORS, OpenAPI, static files)
│   ├── WebApp.java              #   j2html server-rendered web UI
│   ├── adapter/
│   │   ├── AAdapter.java        #     Adapter contract + token/alias mapping
│   │   ├── convex/CVMAdapter    #     Convex (CVM) — CAD29 / slip44 assets
│   │   ├── evm/EVMAdapter       #     EVM via web3j — ERC20 / slip44 assets
│   │   ├── tezos/TezosAdapter   #     Tezos via TzKT HTTP API
│   │   └── kafka/Kafka.java     #     Audit-log sink (HTTP Kafka bridge)
│   ├── api/                     #   RestAPI (/api/v1) + request model records
│   ├── client/Client.java       #   Java client for the TokEngine REST API
│   └── exception/               #   PaymentException, ResponseException
├── src/main/resources/tokengine/
│   ├── config-default.json      #   Smoke-test config
│   ├── config-test.json         #   Config used by the JUnit suite
│   └── pub/                     #   Static web assets (Pico CSS)
├── src/test/java/tokengine/     # JUnit 6 suite (see "Testing" — hits live networks)
├── infra/ansible/               # Deployment: Caddy, Kafka (KRaft + bridge), systemd unit
├── config-example.json          # Annotated JSON5 operator config template
├── walkthrough.json             # Config for WALKTHROUGH.md
├── Dockerfile                   # Multi-stage build → convexlive/tokengine
└── BUILD.md                     # Maven + Docker build/publish instructions
```

## Requirements

- **Java 21+** (`maven.compiler.release` is 21; Docker image builds on Temurin 23)
- **Maven 3.6.3+** (enforced by maven-enforcer-plugin; BUILD.md suggests 3.9.x)
- **Convex 0.8.4** — pinned to the Maven Central release. Unlike Covia, this
  repo does **not** need a local Convex build; a clean clone builds standalone.
  Only build `../convex` first if you deliberately move `convex.version` to a
  `-SNAPSHOT`.

## Build and run

```bash
mvn clean install            # full build; assembly plugin emits target/tokengine.jar
mvn clean install -DskipTests
mvn clean test-compile       # fast compile check, no network needed
mvn test -Dtest=EngineTest   # single test class

java -jar target/tokengine.jar                 # uses ~/.tokengine/config.json
java -jar target/tokengine.jar walkthrough.json # explicit config path (single positional arg)
```

- **Main class:** `tokengine.TokengineMain`
- **Executable JAR:** `target/tokengine.jar` (fat JAR; built in the `install`
  phase, deliberately not attached as a Maven artifact)
- **Default port:** 8080 — web UI at `/`, Swagger at `/swagger`, OpenAPI at
  `/openapi`
- Docker build/publish steps are in `BUILD.md`.

## Testing

**The test suite talks to live public endpoints and is not hermetic.** Expect
failures when offline, when a public endpoint is rate-limiting, or when test
funds/transactions have moved. Nothing is tagged or `@Disabled`, so there is no
offline subset — treat red tests as "confirm the cause" rather than "you broke
it", and check against `git stash` before assuming a regression.

Endpoints reached by `src/main/resources/tokengine/config-test.json` and the
tests themselves:

| Test | External dependency |
|------|--------------------|
| `EngineTest`, `ClientTest`, `ConvexTest` | Embedded Convex peer (ports 18080/18888), Kafka bridge at `kfk.walledchannel.net` |
| `EVMTest` | Ethereum Sepolia via `https://sepolia.drpc.org` |
| `TezosTest` | Tezos Ghostnet via `https://api.ghostnet.tzkt.io/` |
| `APITest` | Local HTTP against the Javalin server |

`ConfigTest` is the only genuinely self-contained class.

Tests run in test mode (`operations.test: true`), which relaxes some security
assertions, uses a seeded peer key, auto-deploys `cad29:test` tokens, and uses
a temporary Etch store.

## Architecture

```
TokengineMain  ──> Engine.launch(config) ──> APIServer.start()
                     │
                     ├── Etch store         (persistent lattice state)
                     ├── Embedded Convex peer (operator connection)
                     ├── Adapters           (one per configured network)
                     └── Kafka              (audit log, best-effort)
```

`Engine` owns all state transitions; adapters own all chain-specific I/O; the
API layer owns only validation and marshalling. Keep that separation — chain
logic must not leak into `RestAPI`, and HTTP concerns must not leak into
`Engine`.

### Lattice state

State lives under `:app / "tokengine"` in the Etch root (see the `Engine` class
docstring):

```
"credits"  -> User Key (AString) -> Token Key (AString) -> balance (AInteger, >= 0)
"receipts" -> Chain ID (AString) -> TX ID (Blob)        -> amount
"config"   -> the config map the engine was last started with
```

The **token key** is the full CAIP-19 asset type — `<chainID>/<assetID>`, e.g.
`convex:main/cad29:132` — produced by `Engine.getTokenKey`. Balance updates and
receipt registration happen inside a single `stateCursor.updateAndGet(...)` so
a deposit can never be credited twice. Keep atomic sections free of network I/O.

### Adapters

`AAdapter<AddressType extends ACell>` is the extension point. It handles token
and alias mapping generically; subclasses supply the chain-specific parts:
`start`/`close`, `parseAddress`, `parseUserKey`, `parseAssetID`,
`toCAIPAssetID`, `parseTransactionID`, `getBalance`, `getOperatorBalance`,
`getOperatorAddress`, `getReceiverAddress`, `checkTransaction`, `payout`,
`verifyPersonalSignature`, `validateSignature`.

`parseAssetID` and `toCAIPAssetID` must round-trip (`EngineTest.testAdapterProperties`
asserts this for every configured token).

To add a network adapter:

1. Subclass `AAdapter` under `tokengine.adapter.<chain>` with a static
   `build(Engine, AMap)` factory.
2. Register the CAIP-2 namespace in `Engine.buildAdapter` (the
   `"convex"` / `"eip155"` / `"tezos"` switch).
3. Override `deployTestAsset` if the chain should support auto-deployed test
   tokens.
4. Add a network entry to `config-test.json` and extend the test suite.

### REST API

`/api/v1/` — `GET status`, `GET adapters`, `GET config`; `POST balance`,
`credit`, `deposit`, `payout`, `transfer`, `wrap`. `balance` is the on-chain
balance; `credit` is TokEngine's virtual balance. OpenAPI docs are generated at
compile time by the Javalin annotation processor from `@OpenApi` annotations —
if you change a route, update its annotation in the same edit.

## Configuration

JSON5 (comments permitted), read via `ConfigUtils.readConfigFile`. Loaded from
`~/.tokengine/config.json` unless a path is given as the single CLI argument.
`config-example.json` is the annotated template.

Four sections matter:

- **`networks`** — adapter instances. `chainID` (CAIP-2) selects the adapter
  type; `alias` is the operator-chosen short name used everywhere else.
- **`tokens`** — cross-chain token metadata, keyed by `alias`.
- **`transfers`** — token alias → network alias → mapping (`assetID`, `symbol`,
  `deposit`/`payout` flags, optional `treasuryID`). The token and network
  aliases must match `tokens` and `networks` exactly. Accepts either a map
  keyed by network alias or a vector of maps carrying a `network` field.
- **`operations`** — `etchFile`, `logDir`, `logConfigFile`, `keyDir`, `kafka`,
  `api-port`, `test`.

`etchFile: "temp"` and `keyDir: "temp"` select throwaway locations — the engine
warns if `temp` is used outside test mode. EVM wallets are loaded from
`<keyDir>/.evm-wallets`.

## Conventions

- **British English** in docs, comments and log messages.
- **Field names** are interned once in `Fields.java` (`Strings.intern`) and
  referenced from there — do not scatter string literals through the code.
- **Data types** are Convex `ACell` types (`AMap`, `AString`, `AInteger`,
  `Blob`, `Index`), not JDK collections. Navigate with `RT.getIn` / `RT.assocIn`
  and coerce with `RT.ensureX`, which return `null` rather than throwing on a
  type mismatch — null-check the result.
- **Money is `AInteger`** in the token's base units. Never `double`.
- **Concurrency:** Javalin runs on virtual threads (`config.useVirtualThreads`).
  All external calls belong on virtual threads; synchronised atomic sections
  stay in-memory and sub-millisecond.
- **Audit logging** via `engine.postAuditMessage` is best-effort — a Kafka
  failure must never fail or roll back a state transition.
- Tabs for indentation in Java sources, matching the existing files.
- Prefer editing existing files to creating new ones.

## Known rough edges

Genuine issues in the current tree — worth knowing before you debug around them:

- `CVMAdapter.getHost()` returns a hardcoded `"localhost:18888"`, so the CVM
  adapter ignores the `url` in its network config and always connects to the
  local embedded peer. Remote CVM networks are not actually reachable.
- `APIServer.close()` is an empty stub, so Jetty is not shut down on close.
- `infra/ansible/files/tokengine.jar` is a build artefact committed to git.
- `dependency-reduced-pom.xml` and `bin/` in the working tree are untracked
  build/IDE leftovers (both gitignored) — ignore them.
- There is no CI: no `.github/workflows`. Verification is whatever you run
  locally.

## Git, GitHub and identity

- **Branches:** `develop` for active development, `master` for releases.
- **Remotes:** `origin` → `Convex-Dev/tokengine`; `ngi` →
  `NGI-TRUSTCHAIN/TokEngine` (project mirror — push there only when asked).
- **Commit identity: this repo has no local `user.name`/`user.email`**, so it
  falls back to the global `mikera` / `mike@mikera.net`. Per the workspace
  `AGENTS.md`, agent work must not be attributed to Mike — set a per-repo bot
  identity (or pass `-c user.name=... -c user.email=...`) before committing, and
  confirm with Mike which identity to use if it has not been configured.
- **`gh` CLI:** act as `brittleboye`; verify with `gh auth status --active`
  before any write operation.

## Security

- **Never commit real keys.** `keystore.pfx` (repo root) and
  `src/test/resources/keys/` hold throwaway test material whose seeds and
  mnemonics are published in `src/test/resources/keys/KEYS.md` — they are public
  knowledge and must never hold real assets.
- Addresses in `config-example.json`, `config-test.json` and `WALKTHROUGH.md`
  are testnet-only examples.
- The operator's receiver and treasury accounts hold real custody in
  production. Changes touching deposit verification, signature validation
  (`verifyPersonalSignature` / `validateSignature`), balance arithmetic or the
  atomic state sections are security-sensitive — flag them explicitly for review
  rather than treating them as routine edits.

## Resources

- TokEngine repo: https://github.com/Convex-Dev/tokengine
- Convex docs: https://docs.convex.world
- CAIP standards: https://chainagnostic.org
- Convex Discord: https://discord.com/invite/xfYGq4CT7v
