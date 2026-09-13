# Automatic Mastery, XP, and Recommendations

Commonplace separates three ideas:

| Measure | Meaning | It does not imply |
| --- | --- | --- |
| Mastery | A conservative estimate of retained topic knowledge | That one narrow worksheet proves a whole topic |
| XP | Verified study activity and effort | Academic ability |
| Recommendation priority | The most useful available practice action | A permanent judgement |

No learning-outcome list or question mapping is required. The model uses information created naturally
while studying.

## Automatic scope and breadth

Every worksheet has an automatic study scope. A Press worksheet uses the specific topic entered for
generation. Manual and imported worksheets use their title. The user does not need to copy that scope
onto individual questions or maintain a separate syllabus structure.

Topic breadth is inferred conservatively from:

- distinct question fingerprints;
- distinct worksheets;
- distinct worksheet scopes;
- independent attempts across different study dates;
- total marks and assessed difficulty.

This is deliberately cautious. Three perfect AVL-rotation answers can show excellent performance on
those questions, but their low breadth and evidence strength keep whole-BST mastery modest. Repeating
the same prompts on the same day adds almost no new evidence. Varied, spaced practice builds the
estimate over time.

## Mastery evidence

Each answer contributes according to:

- awarded marks relative to available marks;
- question mark value and difficulty;
- whether the answer was locked before the mark scheme appeared;
- whether a hint or mark scheme assisted the answer;
- active answering time;
- prompt novelty and repetition;
- worksheet and scope diversity;
- distinct study dates and spacing;
- recent trend, consistency, and elapsed time;
- later active-recall results from the mistake bank.

Per-question evidence is capped. Delayed successful recall increases estimated stability, while
inactivity lowers retained mastery and eventually makes review due. The topic screen displays recent
performance, retention, evidence certainty, question/worksheet/scope breadth, study dates, and the
next review date so the mastery number is not presented with false precision.

Module mastery weights topic estimates by importance and a bounded question-breadth proxy. This avoids
letting a tiny topic dominate a broad module while not rewarding unlimited question creation.

## Attempt integrity

For self-marked work, the learner writes and locks each answer before revealing the mark scheme. Using
the scheme as a hint records assistance and reduces both evidence and XP. An attempt must contain one
valid answer for every worksheet question, and submitted maximum marks must match the stored question.

Attempt creation, answers, mistakes, worksheet statistics, mastery, and practice XP commit in one
database transaction. Reflection is optional and happens only after the completed attempt is safe.

## XP and study habits

XP uses an idempotent event ledger. Practice XP accounts for distinct questions, marks, difficulty,
score, assistance, active time, improvement, and delayed recall. Repeated same-day prompts earn little
or nothing, and per-attempt, category, and daily caps prevent farming.

Meaningful reflection can earn a small one-time reward. Opening a mistake or marking it revisited earns
nothing; only successful unassisted recall can earn review XP, at most once per question and day.

XP bands are labelled **Getting started**, **Building a habit**, **Regular learner**, **Steady learner**,
and **Dedicated learner**. They describe study consistency, not competence. Streaks count completed
study sessions based on the configured distinct-worksheet goal.

## Recommendation flow

Recommendations are deterministic and explainable:

1. Calculate topic and worksheet evidence automatically.
2. Estimate mastery, uncertainty, retention, trend, consistency, and mistake risk.
3. Filter worksheets through the learner's preferred difficulty range.
4. Score need, review timing, uncertainty, difficulty fit, recent variety, importance, exam urgency,
   and mistake severity on normalized scales.
5. Select the highest-priority unused worksheet; **Pick another** advances through ranked alternatives.
6. If there are no eligible saved worksheets, show no recommendation and leave worksheet creation as
   a separate, deliberate action.

Completing the configured daily worksheet goal pauses automatic recommendations until the saved
worksheet interval refreshes; the dashboard shows that state and its countdown. Manually opened
worksheets remain available. The module name is never sent as a Press parameter; only the subject and
specific topic entered in the Press panel are sent. Press remains available from the normal worksheet
creation screen and is never inserted as a recommendation.

## Persistence and migration

The additive schema stores per-answer evidence fields, worksheet scope, XP events, recall reviews, and
recommendation actions. Older outcome-mapping tables from the short-lived outcome-based model may
remain in an upgraded SQLite file for data safety, but the app neither reads them nor asks the user to
maintain them.

Before an older pre-learning database receives its first learning migration, Commonplace creates a
one-time safety copy at:

```text
~/.commonplace/appdata.before-learning-v1.db
```
