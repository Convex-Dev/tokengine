# TokEngine

TokEngine implements Cross-DLT Token exchange using Lattice Technology and [Convex](https://convex.world)

The official [TokEngine repository](https://github.com/Convex-Dev/tokengine) is hosted on GitHub

## Fundamentals:  Atomic Exchange

The core of TokEngine is an atomic state machine that verifies incoming payments and produces outgoing transactions.

Key features:
- High performance asynchronous API server
- Connectivity to multiple source DLTs
- Use of CAIP standards to allow specification of token assets on any DLT 
- Supports any fungible tokens that can be transferred using verifiable DLT transactions
- Verification of incoming payments on source DLTs, according to operator-defined security levels
- Atomic update of user balance + record of incoming transaction. This gets logged (once) if successful
- Atomic decrement of user balance for outgoing transaction

TokEngine is designed to never create and store an inconsistent state, regardless of external failures. This is enforced by state transitions on a merkle tree lattice data structure.

### Basic Operator usage

1. Download or build `tokengine.jar` ([snapshots available here](https://drive.google.com/drive/folders/1AZdyuZOmC70i_TtuEW3uEKvjYLOqIMiv))
2. Place a config file in your home directory at `~/.tokengine/config.json`
3. Run using Java 21+ with `java -jar tokengine.jar`

This should launch the TokEngine server. By default, a simple web interface and API definitions are available on [localhost:8080](http://localhost:8080)

### CAIP Definitions

#### Chain ID

A [CAIP-2](https://chainagnostic.org/CAIPs/caip-2) Chain ID identifies a unique DLT network used by TokEngine

Examples:

- `convex:test` - Local Convex testnet
- `eip155:11155111` - Ethereum Sepolia TestNet
- `eip155:1` - Ethereum Mainnet

#### Account ID

A [CAIP-10](https://chainagnostic.org/CAIPs/caip-10) Account ID identifies a unique account on a DLT. It may be prefixed with the chain ID to specify which DLT the account is located on.

- `eip155:1:0xab16a96D359eC26a11e2C2b3d8f8B8942d5Bfcdb` - Account on Ethereum Mainnet
- `convex:main:56756` - Account `#56756` on Convex main network
- `bip122:000000000019d6689c085ae165831e93:128Lkh3S7CkDTBZ8W7BbpsN3YYizJMp8p6` - Bitcoin account

#### Asset Type

A [CAIP-19](https://chainagnostic.org/CAIPs/caip-19) Asset Type identifies a token on a specific DLT

- `eip155:1/slip44:60` - ETH on Ethereum Mainnet
- `convex:main/slip44:864` - CVM on Convex main network
- `eip155:11155111/erc20:0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238` - USDC ERC20 Stablecoin on Sepolia Testnet

Note use of SLIP-44 to define native tokens

## Example usage

Typical usage of TokEngine is to make a deposit on one network (e.g. EVM), then pay out equivalent funds on a different network (e.g. CVM)

### Make a transfer to the tokengine receiver address 

This can be done with any wallet.

e.g. transaction `0x9d3a3663d32b9ff5cf2d393e433b7b31489d13b398133a35c4bb6e2085bd8e83` on Ethereum Sepolia transfers some USDC to `0x5FbE74A283f7954f10AA04C2eDf55578811aeb03`

(Here USDC is the contract address `0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238`

The destination address must be the receverAddress configured for that tokenm on TokEngine

### Register deposit

Call the `deposit` endpoint to inform TokEngine of the deposit. The JSON payload should look like:

```
{
  "source": {
    "account": "0xab16a96D359eC26a11e2C2b3d8f8B8942d5Bfcdb",
    "network": "eip155:11155111",
    "token": "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
  },
  "deposit": {
    "tx": "0x9d3a3663d32b9ff5cf2d393e433b7b31489d13b398133a35c4bb6e2085bd8e83"
  }
}
```

TokEngine will check that:
- The deposit has been made and signed correctly
- The deposit transaction has not already been claimed, or is otherwise invalid

If successful, virtual credit will be given to the sender address

### Make a payout

Once virtual credit is available, a payout can be made to a target network, given proof of possession of the required private key

This is done by a request to the `api/v1/payout` endpoint, with a request structured like:

```
{
  "source": {
    "account": "#11",
    "network": "convex:test",
    "token": "WCVM"
  },
  "destination": {
    "account": "#13",
    "network": "convex:test",
    "token": "CVM"
  },
  "deposit": {
    "tx": "0x9d3a3663d32b9ff5cf2d393e433b7b31489d13b398133a35c4bb6e2085bd8e83",
    "msg": "Transfer 100000 to #13 on convex",
    "sig": "0xdd48188b1647010d908e9fed4b6726cebd0d65e20f412b8b9ff4868386f05b0a28a9c0e35885c95e2322c2c670743edd07b0e1450ae65c3f6708b61bb3e582371c"
  },
  "quantity": "100000"
}
```

Note the `sig` field must be a correct signature of the `msg` for the address of the sender, to ensure only the authorised depositor is able to pay out their own virtual funds.



## Overall design

### Deposit

0. User sends deposit on source DLT (client app)
    - User sends tokens on source DLT to TokEngine operator receiver account
    - User gets confirmed transaction ID (from source DLT)

1. Validation (async)
    - Validate API inputs for validity / correct format
    - Validate that the asset specified is configured to receive deposits by the operator
    - Validate that a deposit transaction has been correctly identified
    - Log valid API request

2. Verification stage (async)
    - Verify transaction has been confirmed on source DLT
    - If pending can wait for finality (optional)
    - Verify transaction had triggered a deposit to the operator's receiver account

3. Atomic update (sync)
    - Check transaction has not already been atomically registered
    - Perform atomic balance update
    - Perform atomic registration of transaction as received
    - Write state
    - Create and write audit trail (if this fails, warning is logged but update is still safe)

4. Confirmation (async)
    - Return confirmation to API user

### Withdraw

1. Validation (async)
    - Validate API inputs for validity / correct format
    - Validate that the asset specified is configured to allow payouts by the operator
    - Validate destination DLT address / specification
    - Log valid API request

2. Verification (async)
    - Verify user has sufficient balance for payout (plus any fees)
    - Verify signature on payout request
    - Construct payout transaction (on dest DLT)

3. Atomic prepare (sync)
    - Record payout transaction (with ID for dest DLT)
    - Lock payout amount from user
    - Write state

4. Transaction execution (async)
    - Submit transaction to target DLT
    - Return transaction ID to API caller
    - Confirm completion of DLT payout transaction

5. Atomic commit (sync, idempotent, can be retried)
    - Check transaction not yet recorded as confirmed / failed
    - Mark payout transaction as confirmed
    - Decrement user balance (payout plus fees)
    - Increment operator fee balance
    - Write state
    - Create and write audit trail (if this fails, warning is logged but update is still safe)

6. Atomic rollback (sync, if transaction fails, idempotent, can be retired)
    - Check transaction not yet recorded as confirmed / failed
    - Record transaction as failed
    - Decrement user balance (if any failure fees due)
    - Increment operator fee balance (if any failure fees due)
    - Unlock user funds
    - Write state
    - Create and write audit trail (if this fails, warning is logged but update is still safe)

7. Confirmation (async polling by client app)
    - Return confirmation / failure status to API user when polled

### Transfer

A transfer is a deposit plus withdraw flow in a single API call. All state updates are identical to a deposit+withdraw.

## Tech notes

The design is intended to support high throughput concurrent usage:
- Atomic sections are free of external interactions and generally run in-memory, completed in less than 1ms
- All external operations are executed on lightweight virtual threads



