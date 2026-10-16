# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The README is the main documentation. It describes the setup, the configuration, the architecture, the development commands, the workflows and the release:

@README.md

## Rules for Changes

- Keep the README current. When you change a configuration key, a command, the architecture or a workflow, update the README in the same commit.
- The README is also the Dokka module documentation. Keep the headings `# Module collector` and `# Package ...`.
- Follow the Detekt rules in the README section "Development". Do not write `TODO`, `FIXME` or `STOPSHIP` comments.
- Run the tests with `JAVA_TOOL_OPTIONS="-Dapi.version=1.44"` on machines with Docker 29 or newer (Testcontainers).
- Follow the rules of the workflows in the README section "GitHub Workflows" for each change of `.github/workflows/`.

## Commit Style

`[TICKET-ID] Short imperative summary`.
