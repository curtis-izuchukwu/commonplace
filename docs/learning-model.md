# Automatic Mastery, XP, and Recommendations

Commonplace uses three related but independent systems. Mastery estimates retained knowledge, XP records useful study activity, and recommendation priority ranks the next available worksheet. None of these values is intended to stand in for the others.

| System | Question it answers | Deliberate safeguard |
| --- | --- | --- |
| Mastery | “How much retained knowledge does the available evidence support?” | Narrow or repeated evidence cannot establish broad topic mastery |
| XP | “How much useful study activity was completed?” | Idempotency and daily caps prevent repeated reward farming |
| Recommendation priority | “Which saved worksheet is the best next action?” | Only eligible, existing worksheets are ranked |

The calculations are transparent product heuristics, not calibrated probabilities or formal educational assessments.

## Evidence captured during study

Each submitted answer records:

- the question and worksheet;
- awarded and available marks;
- assessed difficulty;
- whether the answer was locked before self-marking;
- whether a hint or mark scheme assisted the answer;
- active answering time;
- whether it was saved as a mistake;
- the worksheet's study scope;
- the attempt time.

Successful and unsuccessful active-recall reviews from the mistake bank become later evidence for the same question fingerprint.

## Scope and breadth

Every question prompt is normalised into a fingerprint by lowercasing it, removing punctuation, and collapsing non-alphanumeric separators. This allows copied or lightly reformatted prompts to be recognised as the same underlying question.

Every worksheet also has a study scope:

- Press worksheets use the specific generation topic entered in the Press panel;
- manual worksheets use their title;
- imported worksheets use their saved title.

Topic breadth is inferred from distinct question fingerprints, worksheets, study scopes, attempts, and study dates. The learner never has to maintain a separate mapping system.

## Mastery estimate

At a high level, topic and worksheet mastery follow this structure:

```text
mastery = performance
        × evidence strength
        × consistency modifier
        × retention
        × assessed-difficulty ceiling
```

The result is displayed as a percentage. Strong performance alone is not enough: the estimate also needs reliable, varied, and retained evidence.

### Answer evidence weight

An answer begins with its available marks, capped at eight marks for evidence weighting. The weight is then adjusted for:

- **Difficulty:** easy answers contribute less evidence; hard answers contribute more.
- **Independence:** a locked, unassisted answer is strongest. Assisted answers are substantially discounted. Legacy or unlocked evidence is weaker.
- **Active time:** useful active time can slightly strengthen locked evidence, up to a limit.
- **Novelty:** the first occurrence of a prompt receives full novelty weight. Same-day repetition receives almost none; delayed returns regain some weight.
- **Per-question cap:** a single fingerprint cannot accumulate unlimited evidence.
- **Age:** old performance evidence decays gradually with a 60-day half-life.

If an answer was assisted, its effective score is also reduced. An answer saved as a mistake cannot contribute an unrealistically high effective score to mastery.

### Evidence strength

Evidence strength rises with the total reliable evidence weight and is multiplied by bounded breadth factors for:

- distinct questions;
- distinct study dates;
- distinct worksheets;
- distinct study scopes.

This is why three perfect questions about AVL rotations can show excellent recent performance while leaving whole-topic mastery low. The evidence is accurate but narrow.

The UI labels evidence strength as:

| Strength | Label |
| --- | --- |
| Below 0.35 | Limited evidence |
| 0.35 to below 0.70 | Developing evidence |
| 0.70 and above | Established evidence |

### Consistency and trend

Consistency is derived from the weighted variance of effective answer scores. Mixed high and low results reduce the consistency modifier, while stable results preserve it.

Trend compares evidence from the last 14 days with older evidence when both are available. It informs recommendation need but does not directly override the underlying mastery evidence.

### Retention and review dates

Retention decays from the most recent evidence according to an estimated stability interval. A successful, unassisted return on a later date increases stability; an unsuccessful return shortens it. Immediate repetition cannot extend stability.

The next review date is based on the latest evidence date, stability, and whether the latest effective score demonstrated successful recall. When that date is reached, the worksheet can receive a **Review is due** recommendation signal.

### Module mastery

Module mastery is a weighted average of its topic estimates. Topic importance affects the weight, while a bounded question-count factor prevents a tiny topic from dominating a broad module without rewarding unlimited question creation.

## XP and study habit

XP measures useful activity, not knowledge. An attempt can be fully saved and still earn zero XP.

### Practice XP

Practice XP uses the same answer record but applies a separate reward calculation. It considers:

- prompt novelty;
- independent versus assisted work;
- question marks and difficulty;
- score;
- active time;
- improvement over prior work;
- successful recall after a gap.

The first attempt at a distinct prompt has the highest novelty. Repeating the same prompt on the same day has zero novelty for XP. A later return can earn XP again, especially after a meaningful delay.

Practice XP is capped at 120 per attempt and 250 per day.

### Reflection XP

A meaningful optional reflection awards 5 XP once per attempt. The combined reflection must contain at least 30 characters and the next action must contain at least 10 characters. Empty, skipped, or repeated reflection submissions do not earn additional XP. Reflection XP is capped at 30 per day.

### Mistake-recall XP

Opening, revisiting, or manually resolving a mistake awards no XP. A successful unassisted recall review awards 10 XP at most once per topic, question fingerprint, and day. Assisted or unsuccessful reviews award none. Recall XP is capped at 30 per day.

### Global safeguards

Every XP event has a unique event key and is inserted into an idempotent ledger. Duplicate events are ignored. The global XP cap is 300 per day across all categories.

Study-habit labels use the following lifetime XP bands:

| XP | Label |
| --- | --- |
| 0 to 499 | Getting started |
| 500 to 1,499 | Building a habit |
| 1,500 to 2,999 | Regular learner |
| 3,000 to 4,999 | Steady learner |
| 5,000 and above | Dedicated learner |

These labels describe sustained participation rather than competence.

### Daily goals and streaks

The daily goal counts distinct worksheets completed on a date. Repeating one worksheet cannot fill multiple goal slots. When streak tracking is enabled, completing the goal starts or extends the session streak if it occurs within the configured worksheet interval. Missing that interval expires the streak.

## Recommendation eligibility

A worksheet is eligible only when it:

- belongs to the signed-in account;
- has at least one saved question;
- falls within the configured minimum and maximum difficulty;
- is not inside a past-exam module when completed-module archiving is enabled;
- has not already been completed today.

Press is never inserted into the recommendation list. Generation remains a deliberate action in the worksheet editor.

## Recommendation signals

For every eligible worksheet, Commonplace calculates a score from 1 to 100. The default balanced weights are:

| Signal | Balanced weight | Meaning |
| --- | ---: | --- |
| Learning need | 0.32 | Mastery gap, performance risk, mistakes, negative trend, and inconsistency |
| Review need | 0.18 | Due-date pressure or fading retention |
| Uncertainty | 0.10 | Lack of strong evidence |
| Importance | 0.08 | Average of topic and worksheet priority |
| Variety | 0.08 | Preference for material not practised very recently |
| Difficulty fit | 0.10 | Match between worksheet difficulty and current evidence |
| Exam urgency | 0.12 | Time to exam, remaining topic need, and worksheet coverage |

The **Weak Topics** focus increases the learning-need weight from 0.32 to 0.42. The **Upcoming Exams** focus increases the exam-urgency weight from 0.12 to 0.22. Scores are normalised after the selected focus is applied.

Learning need is itself composed of 55% mastery gap, 30% performance risk, and 15% inconsistency. Performance risk takes the strongest signal from recent performance, unresolved or optionally resolved mistake risk, and negative trend.

Difficulty fit avoids recommending hard material too aggressively when performance and evidence strength are low. Exam urgency grows as an upcoming exam approaches and is distributed across the saved worksheets covering that topic.

## Selection and refresh behaviour

Eligible worksheets are sorted by descending priority score, then by a stable worksheet key. Selection is deterministic.

- The current recommendation remains stable when it is still eligible and within five points of the current leader.
- **Pick another** chooses the highest-ranked worksheet not yet selected that day.
- When every alternative has been seen, selection cycles back through the ranked list without repeating the current item when possible.
- Completing a worksheet excludes it for the rest of the day.
- Completing the daily goal pauses automatic recommendations until the worksheet interval unlocks again.
- If worksheets exist but the daily window is locked, the dashboard shows a completion message and countdown.
- If no eligible saved worksheet exists, the dashboard shows an empty state rather than offering automatic worksheet creation.

The recommendation explanation exposes up to three leading signals, such as **Review is due**, **More evidence needed**, **Worth reinforcing**, **Exam approaching**, **Adds variety**, or **Stretch difficulty**.

## Persistence and migration

The learning schema adds answer assistance, active time, evidence mode, initial answer, question difficulty, worksheet scope, generation subject, XP events, mistake reviews, and recommendation-action history.

Migration is additive. Existing attempts are retained and are not replayed for XP. Older learning-outcome tables may remain in an upgraded database for safety, but the current application neither reads them nor exposes learning-outcome management in the UI.

Before an older database receives its first learning-schema migration, Commonplace creates one consistent SQLite snapshot at:

```text
~/.commonplace/appdata.before-learning-v1.db
```

## Interpretation guide

- A high latest score with low mastery usually means the evidence is narrow, recent, assisted, or insufficiently spaced.
- A lower retention value means the material is becoming due; it does not erase prior performance.
- Zero XP does not mean an attempt was ignored. Attempts, marks, mistakes, and mastery evidence save independently of the reward amount.
- Recommendation priority is temporary and contextual. It changes as evidence, dates, settings, mistakes, and completed worksheets change.
