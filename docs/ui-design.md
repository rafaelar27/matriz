# Matriz — UI visual design

## Status

Drafted on 2026-11-19 and approved by the project decision-maker (see the [decision log](constraints.md#decision-log)). It describes the look only. No technology is chosen by this document (TD-01 stays pending). Requirement IDs are defined in [requirements.md](requirements.md), user stories in [user-stories.md](user-stories.md) and pending decisions in [constraints.md](constraints.md#pending-product-decisions).

## Colour tokens

| Name | Hex | Use | Contrast |
|---|---|---|---|
| Accent (Plum) | #7B009B | Primary buttons, progress fill, links, focus ring | White text on it is about 9:1 |
| Accent dark | #650080 | Hover state, selected text | — |
| Accent tint | #F4E8F8 | Selected nav item, "In progress" background | — |
| Logo light squares | #E2C4EB | Light squares of the logo | — |
| Ink | #18141B | Main text | — |
| Ink secondary | #4F4956 | Secondary text | — |
| Muted | #6A6472 | Labels | About 5.3:1 on the canvas colour |
| Body dark | #332E38 | Long text | — |
| Canvas | #F7F5F8 | Page background | — |
| Surface | #FFFFFF | Cards | — |
| Line | #E7E3EA | Borders | — |
| Divider | #F1EEF2 | Dividers | — |
| Track | #ECE8EE | Progress track | — |
| Control border | #DAD4DE | Borders of form controls | — |
| Segmented background | #F3F0F4 | Background of segmented controls | — |
| Overdue (Ember) | #B3400C on #FDF0E8 | Overdue mark | — |
| Done (Teal) | #0E6B62 on #E3F3F0 | "Done" status label | — |
| Destructive border | #E8C9B8 | Border of destructive actions | — |

## Typography

- **Headings:** Space Grotesk, weight 600. Page title 32px; card and section title 18px.
- **Everything else:** Inter. Body text weight 400 at 15px; UI text weight 500 at 14px; field labels weight 500 at 12px in capitals with 0.06em letter spacing.

## Shape and spacing

- Corner radius: 8–10px on controls, 14–16px on cards.
- Clickable areas are at least 40–44px tall.
- Focus: a 2px outline in the accent colour, offset 2px.
- Logo: a 3×3 grid of rounded squares.

## Status and signals

- **Status labels:** "To do" (white with a grey outline), "In progress" (accent tint), "Done" (teal).
- **Overdue mark:** a warning icon plus the word "Overdue".
- **Progress:** the text "N of M tasks done" next to the bar.
- Meaning never depends on colour alone (FR-12, FR-18, FR-19, NFR-06).

## Screens drafted

| Screen | Requirements covered |
|---|---|
| Projects | FR-08, FR-18 |
| Project task list | FR-13, FR-15, FR-19 |
| Task detail | FR-12, FR-16, FR-19 |
| My tasks | FR-14, FR-19 |

Design canvas: https://claude.ai/artifact/25g3uNB9KMjsoHVaYGH6bu

## Assumptions

These are assumptions, not decisions.

- The interface is in English (PD-13).
- Progress shows no percentage (PD-07).
- Done tasks are left out of My tasks (PD-06).
- Sample names and dates in the drafts are test data.
