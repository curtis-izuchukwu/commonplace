# ParārePilot V1 Manual Test Checklist

## App Launch

- [ ] App launches with `mvn javafx:run`
- [ ] Dashboard appears first
- [ ] No startup exceptions appear

## Module and Topic Management

- [ ] User can create a module
- [ ] User can create a topic under a module
- [ ] User can reopen the app and still see the module/topic
- [ ] Topic detail opens correctly

## Worksheet Creation

- [ ] User can create a worksheet under a topic
- [ ] User can add multiple questions
- [ ] Each question saves prompt, mark scheme, and max marks
- [ ] Worksheet detail shows all questions after restart

## Attempt Flow

- [ ] User can start an attempt
- [ ] User can answer each question
- [ ] User can reveal mark schemes
- [ ] User can award marks
- [ ] User can mark answers as mistakes
- [ ] Attempt saves successfully

## Reflection and Stats

- [ ] Reflection screen appears after attempt
- [ ] Confidence can be selected
- [ ] Main weakness, next action, and notes save
- [ ] Worksheet latest score updates
- [ ] Worksheet average score updates
- [ ] Worksheet attempt count updates
- [ ] Topic mastery updates

## Mistake Bank

- [ ] Marked mistakes appear in mistake bank
- [ ] Mistake shows topic, worksheet, prompt, answer, mark scheme, and note
- [ ] Mistake can be marked revisited
- [ ] Mistake can be marked resolved
- [ ] Resolved mistakes can be shown/hidden

## Dashboard

- [ ] Dashboard shows recommended worksheet
- [ ] Recommendation can be opened
- [ ] Dashboard shows XP/rank
- [ ] Dashboard shows streak
- [ ] Dashboard shows unresolved mistake count
- [ ] Dashboard shows weakest topics
- [ ] Dashboard shows recent attempts

## Weighted Selection

- [ ] Old worksheets receive higher priority
- [ ] Low-scoring worksheets receive higher priority
- [ ] Low-confidence topics receive higher priority
- [ ] Important worksheets/topics receive higher priority
- [ ] Worksheets with unresolved mistakes receive higher priority
- [ ] Refresh recommendation can select different worksheets

## Persistence

- [ ] Close and reopen app
- [ ] Modules persist
- [ ] Topics persist
- [ ] Worksheets persist
- [ ] Questions persist
- [ ] Attempts persist
- [ ] Reflections persist
- [ ] Mistakes persist
- [ ] XP/streak persist