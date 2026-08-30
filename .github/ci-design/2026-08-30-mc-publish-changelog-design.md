# mc-publish Changelog Design

## Goal

Keep the full release body, including the collapsible full commit list, on the
GitHub Release while publishing only the summary portion to Modrinth and
CurseForge.

## Scope

Update the standard and nightly release workflows. The existing release-note
generation and formatting remain unchanged.

## Design

The release-note step will expose two outputs:

- `body`: the complete generated release body for GitHub.
- `summary`: the same body truncated at the first `<details>` block.

The `mc-publish@v3` step will use its platform-specific changelog inputs:

- `github-changelog`: `body`
- `modrinth-changelog`: `summary`
- `curseforge-changelog`: `summary`

If a generated body has no `<details>` block, `summary` will equal the complete
body. This preserves the release notes if the formatting changes later.

## Alternatives Considered

- Add a summary mode to `format-release-notes.sh`: more explicit, but expands
  the script interface and requires additional synchronization.
- Run separate publishing steps per platform: provides isolation, but repeats
  publishing configuration and increases workflow complexity.

## Verification

Verify both workflow files contain the three platform-specific changelog
inputs, test truncation with bodies both containing and omitting `<details>`,
and run the project's required Gradle test tasks sequentially.
