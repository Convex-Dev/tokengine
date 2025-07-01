# TokEngine

Cross-DLT Token exchange 

## Fundamental Atomic Exchange

The core of TopkEngine is an atomic state machine that verifies incoming payments and produces outgoing transactions.

Key features:
- Verification of incoming payments on source DLTs, according to operator-defined security levels
- Atomic update of user balance + record of incoming transaction. This gets logged (once) if successful
- Atomic decrement of user balance for outgoing transaction

TokEngine is designed to never create and store an inconsistent state, regardless of external failures.


### Deposit

1. Validation stage (async)
- Validate API inputs for validity / correct format
- Validate that the asset specified is configured to receive deposits by the operator
- Log valid API request

2. Verfication stage (async)
- Verify transaction has been confirmed on source DLT
- Verify transaction had triggered a deposit to the operator's receiver account

3. Atomic update (sync)
- Check transaction has not already been atomically registered
- Perform atomic balance update
- Perform atomic registration of transaction as received
- Write state
- Create and write audit trail (if this fails, warning is logged but update is still safe)

4. Confirmation stage (async)
- Return confirmation to API user

### Withdraw

1. Validation stage (async)
- Validate API inputs for validity / correct format
- Validate that the asset specified is configured to allow payouts by the operator
- Log valid API request

2. Verfication stage (async)
- Verify user has sufficient balance for payout (plus any fees)
- Construct payout transaction

3. Atomic prepare (sync)
- Record payout transaction
- Write state

4. Transaction execution stage (async)
- Sumbit transaction to target DLT
- Confirm completion of DLT payout transaction

5. Atomic commit (sync, idempotent, can be retried)
- Mark payout transaction as confirmed
- Decrement user balance (payout plus fees)
- Increment operator fee balance
- Write state
- Create and write audit trail (if this fails, warning is logged but update is still safe)

6. Atomic rollback (sync, if transaction fails)
- Record transaction as failed
- Decrement user balance (if any failure fees due)
- Increment operator fee balance (if any failure fees due)
- Write state
- Create and write audit trail (if this fails, warning is logged but update is still safe)

7. Confirmation stage (async)
- Return confirmation to API user

### Transfer

A transfer is a deposit plus withdraw flow in a single API call. All state updates are identical to a deposit+withdraw.



