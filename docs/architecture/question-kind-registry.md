# Question-kind registry

`QuestionKindRegistry` is the closed, immutable source of truth for the 14
published quiz kinds. Each descriptor binds the FE authoring alias to a runtime
kind, grading policy, answer-key shape and grading rule. The compiler,
publish-validator and grading engine consume the same descriptor; they do not
maintain independent raw string lists.

| Runtime kind | Authoring alias | Policy | Answer key | Grader |
| --- | --- | --- | --- | --- |
| `single_choice` | `multiple-choice` | auto | exact choice | exact choice |
| `multiple_response` | `multiple-response` | auto | choice set | multiple response |
| `true_false` | `true-false` | auto | exact choice | exact choice |
| `short_answer` | `short-answer` | auto | accepted answers | short answer |
| `numeric` | `numeric` | auto | value/tolerance | numeric |
| `sequence` | `sequence` | auto | order | sequence |
| `matching` | `matching` | auto | pairs | pair ratio |
| `fill_blank` | `fill-in-the-blanks` | auto | blanks | blanks |
| `select_from_lists` | `select-from-lists` | auto | blanks | blanks |
| `drag_words` | `drag-the-words` | auto | blanks | blanks |
| `hotspot` | `hotspot` | auto | regions | hotspot |
| `drag_drop` | `drag-and-drop` | auto | targets | pair ratio |
| `likert_scale` | `likert-scale` | survey | none | survey/no score |
| `essay` | `essay` | manual | none | manual review |

Unknown authoring types are ignored as non-question content by the compiler.
Unknown runtime kinds fail with an explicit unsupported-kind error; they are not
graded as zero. A future kind must add one descriptor and its focused fixture
matrix before it can be published. Manual and survey kinds are intentional
non-auto-gradable exceptions, not accidental registry gaps.
