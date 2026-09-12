# Weighted Worksheet Selection

Commonplace does not simply pick the highest-priority worksheet.

Instead, every worksheet receives a priority score, then the app performs weighted-random selection. This means weaker or neglected worksheets appear more often, while recommendations still feel varied and unpredictable.

## Overview

| Stage              | Description                                                                                                                       |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| Priority scoring   | Each eligible worksheet is assigned a score based on performance, confidence, age, difficulty, importance, failures, and mistakes |
| Weighted selection | Worksheets with higher scores become more likely to appear                                                                        |
| Randomisation      | The final choice is still random, so the same worksheet is not always selected                                                    |
| Recommendation     | The selected worksheet is shown to the user as the next recommended worksheet                                                     |

## Priority Factors

Each worksheet is scored using the following factors:

| Factor                   | Meaning                                                               |
| ------------------------ | --------------------------------------------------------------------- |
| Age since last attempt   | Older worksheets become more likely to appear                         |
| Latest score             | Worksheets with lower recent scores receive a boost                   |
| Topic confidence         | Topics marked with lower confidence receive a boost                   |
| Worksheet difficulty     | More difficult worksheets may receive a priority boost                |
| Worksheet importance     | Worksheets marked as important become more likely to appear           |
| Topic importance         | Topics marked as important increase the priority of their worksheets  |
| Failure streak           | Worksheets failed repeatedly receive a stronger boost                 |
| Unresolved mistake count | Worksheets linked to unresolved mistakes become more likely to appear |

## Score Formula

```text
priority =
  base
  + ageScore
  + lowScoreBoost
  + confidenceBoost
  + difficultyBoost
  + importanceBoost
  + failureBoost
  + mistakeBoost
```

The final priority value is clamped between `1` and `100`.

## Score Component Summary

| Component         | Purpose                                                                 |
| ----------------- | ----------------------------------------------------------------------- |
| `base`            | Ensures every worksheet has a minimum priority                          |
| `ageScore`        | Increases priority for worksheets that have not been attempted recently |
| `lowScoreBoost`   | Increases priority for worksheets with poor recent performance          |
| `confidenceBoost` | Increases priority for topics where the user has low confidence         |
| `difficultyBoost` | Adjusts priority based on worksheet difficulty                          |
| `importanceBoost` | Increases priority for important worksheets and topics                  |
| `failureBoost`    | Increases priority when the user has repeatedly failed a worksheet      |
| `mistakeBoost`    | Increases priority when unresolved mistakes are linked to the worksheet |

## Weighted Random Algorithm

| Step | Action                                                                                   |
| ---- | ---------------------------------------------------------------------------------------- |
| 1    | Load all eligible worksheets                                                             |
| 2    | Calculate the priority score for each worksheet                                          |
| 3    | Add all priority scores together                                                         |
| 4    | Generate a random number between `0` and the total score                                 |
| 5    | Iterate through the worksheets, subtracting each worksheet's score from the random value |
| 6    | Select the worksheet where the random value falls                                        |

## Example

| Worksheet   | Priority Score | Selection Chance |
| ----------- | -------------: | ---------------: |
| Worksheet A |             10 |              Low |
| Worksheet B |             35 |           Medium |
| Worksheet C |             80 |             High |
| Worksheet D |             20 |       Medium-low |

In this example, `Worksheet C` is the most likely to be selected, but it is not guaranteed. The other worksheets still have a chance to appear.

## Why Weighted Random?

A deterministic system would repeatedly select the single highest-priority worksheet. That would become predictable, repetitive, and frustrating.

Weighted randomness keeps the app adaptive while still giving the user variety. The system can favour weak areas without making the daily worksheet feel completely fixed or mechanical.

## Behaviour Summary

| System Type                | Behaviour                                   |
| -------------------------- | ------------------------------------------- |
| Pure random selection      | Gives variety, but ignores weak areas       |
| Highest-priority selection | Targets weak areas, but becomes repetitive  |
| Weighted-random selection  | Targets weak areas while preserving variety |

Commonplace uses weighted-random selection because it balances structure with unpredictability.
