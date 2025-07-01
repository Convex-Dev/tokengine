# TokEngine

Cross-DLT Token exchange 

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

TokEngine is designed to never create and store an inconsistent state, regardless of external failures.


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



