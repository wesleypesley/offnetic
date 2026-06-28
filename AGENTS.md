# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## 5. Verifying (per Part, before "done")

After implementation compiles and tests pass, run these before marking a Part complete. The build passing is NOT sufficient proof of correctness — the real bugs live outside the happy path.

### 5a — Run the full suite
- Run `:app:testDebugUnitTest` (not just the changed tests). Do NOT run `assembleDebug` — the debug APK dexing fails on libsignal; only release APKs build currently.
- If live network tests exist, they must be skipped by default (gated via `assumeTrue`/env var) and run manually on demand. The deterministic suite must always be 100% green, offline.

### 5b — Not-wired check
- Grep `app/src/main` for the new class/function names. They should appear ONLY in their own files + DI modules, and NOT be consumed by unintended production paths (unless the Part intentionally wires them).

### 5c — No new warnings
- Scan the build output. Fix any compiler warnings introduced by the new code. Pre-existing warnings are noted but not fixed.

### 5d — Edge-case / logic audit
Trace every code path in the new or changed code. Look for:

- **State machine errors** — illegal transitions, stuck states, races between concurrent state changes.
- **Concurrency issues** — shared mutable state without synchronization, coroutine cancellation leaving partial state.
- **Missing error handling** — what if a dependency returns null, a network call fails, a DAO throws, a crypto operation produces garbage, a DB row was deleted between reading and writing.
- **Unit mismatches** — unix seconds vs millis; base64url vs base64-standard; hex vs npub; String vs ByteArray.
- **Silent failure modes** — an exception kills a coroutine and nothing restarts it; a message is dropped without a log; an error is set in a ViewModel but never displayed.
- **Happy-path-only guards** — code that works when all dependencies are connected but silently degrades (or crashes) when one is unavailable.
- **Idempotency / replay** — what happens if a message/gift-wrap/request is re-processed (multi-relay delivery, re-subscription, re-publish).

The logic audit is a defensive re-read of the actual code, not a checkbox against the plan.

### 5e — Verification by Part type
- **Pure crypto / protocol** → cross-check against official external vectors (BIP-340, NIP-44, NIP-59). Their pass is the interop proof.
- **DB / DAO / Room** → Robolectric in-memory DB tests.
- **Relay transport / glue** → compile + full suite green + logic audit.
- **UI (Compose)** → compile + full suite green, plus visual/manual verification when a device is available.
- **DI / Hilt wiring** → compile validates the graph. No additional test needed.

### 5f — Verify command (Windows PowerShell)
```
& .\gradlew.bat :app:testDebugUnitTest --console=plain
```
