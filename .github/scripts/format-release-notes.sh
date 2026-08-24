#!/usr/bin/env bash
set -euo pipefail

if [ "${1:-}" = "--full" ]; then
    awk '{ hash = $1; sub(/^[^ ]+ /, ""); if (NF) print "- " $0 " (" hash ")" }'
    exit 0
fi

version="$1"
commit_data="$(cat)"
commit_count="$(printf '%s\n' "$commit_data" | awk 'NF { count++ } END { print count + 0 }')"

summary="$(awk '
function add(category, subject) {
    count[category]++
    entries[category, count[category]] = subject
}
{
    if ($0 == "") next
    subject = $0
    sub(/^[^ ]+ /, "", subject)
    category = "Other"
    if (subject ~ /^feat(\([^)]*\))?!?:[[:space:]]*/) {
        category = "Features"
        sub(/^feat(\([^)]*\))?!?:[[:space:]]*/, "", subject)
    } else if (subject ~ /^fix(\([^)]*\))?!?:[[:space:]]*/) {
        category = "Fixes"
        sub(/^fix(\([^)]*\))?!?:[[:space:]]*/, "", subject)
    } else if (subject ~ /^perf(\([^)]*\))?!?:[[:space:]]*/) {
        category = "Performance"
        sub(/^perf(\([^)]*\))?!?:[[:space:]]*/, "", subject)
    } else if (subject ~ /^refactor(\([^)]*\))?!?:[[:space:]]*/) {
        category = "Refactoring"
        sub(/^refactor(\([^)]*\))?!?:[[:space:]]*/, "", subject)
    } else if (subject ~ /^test(\([^)]*\))?!?:[[:space:]]*/) {
        category = "Tests"
        sub(/^test(\([^)]*\))?!?:[[:space:]]*/, "", subject)
    } else if (subject ~ /^docs(\([^)]*\))?!?:[[:space:]]*/) {
        category = "Documentation"
        sub(/^docs(\([^)]*\))?!?:[[:space:]]*/, "", subject)
    } else if (subject ~ /^(build|ci|dep|chore)(\([^)]*\))?!?:[[:space:]]*/) {
        category = "Maintenance"
        sub(/^(build|ci|dep|chore)(\([^)]*\))?!?:[[:space:]]*/, "", subject)
    }
    if (subject == "") subject = "(no subject)"
    add(category, subject)
}
END {
    category_count = split("Features Fixes Performance Refactoring Tests Documentation Maintenance Other", order, " ")
    for (i = 1; i <= category_count; i++) {
        category = order[i]
        if (count[category] == 0) continue
        print "## " category
        for (j = 1; j <= count[category]; j++) print "- " entries[category, j]
        print ""
    }
}' <<< "$commit_data")"

if [ -z "$summary" ]; then
    summary="- No changes found."
fi

full_list="$(bash "$0" --full <<< "$commit_data")"
if [ -z "$full_list" ]; then
    full_list="- No changes found."
fi

printf '# Release %s\n\n**%s commits**\n\n%s\n\n<details>\n<summary>Full commit list</summary>\n\n%s\n\n</details>\n' \
    "$version" "$commit_count" "$summary" "$full_list"
