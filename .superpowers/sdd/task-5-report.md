# Task 5 Report

## Changes

- Replaced JEI item input ingredients with component-preserving `ItemInputDisplay` stacks.
- Applied exact component predicates to every item-tag candidate and surfaced non-exact predicates in input tooltips.
- Added red Keep/consumption-percent overlays and matching EN/zh_CN translations without changing output chance rendering.

## Verification

- `./gradlew compileJava --no-daemon` passed.

## Scope

- Tests were neither added nor run, per the task request.
