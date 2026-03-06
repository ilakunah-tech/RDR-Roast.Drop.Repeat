# Orchestrate — Multi-Agent Workflow

Run the full orchestration workflow for the task described below (or the user's attached message).

## Workflow (strict order)

1. **Planner** — Restate the task, break into 3–7 concrete steps, list files to edit/create, risks/assumptions, and acceptance criteria.
2. **Reference Researcher** — Inspect `C:\Users\ilaku\Downloads\1_extracted` and `D:\project\Projects\artisantest-master`. Follow `.cursor/rules/reference-cropster.mdc` as a hard rule. Return exact relevant paths; separate mirror / adapt / do-not-copy.
3. **Implementer** — Implement only after receiving Planner and Reference Researcher outputs. Keep changes scoped; follow project conventions.
4. **Reviewer** — Check plan compliance, reference alignment, unnecessary changes, correctness, edge cases, consistency, maintainability.
5. **Tester** — Run `./gradlew build -x test` and `./gradlew test`. Report Compile / Build / Test status factually. Classify failures clearly.

## Orchestrator rules

- No implementation before planning and reference research are complete.
- If Reviewer fails → send back to Implementer once with clear feedback.
- If Tester fails → return to Implementer with exact failure details (build/test output, error messages, file:line).
- Produce final status: `done` | `blocked` | `needs review`.

## Final summary (required)

After all steps, produce:

- **Summary**: One-paragraph description of what was done.
- **Files changed**: List of paths.
- **Review issues**: Critical/warning items or "none".
- **Test status**: Pass / Fail with details.
- **Remaining risks**: Known limitations or follow-ups.

## Task

[Describe the task here, or use the user's message if provided after /orchestrate]
