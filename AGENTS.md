# General Coding Guidelines

- Apply the principles of Clean Code, KISS, and DRY
- Complex code should have comments.
    - Large files are complex
    - Code with lots of nested conditional or loops is complex
- All files should have at least _some_ comments (e.g. at the class-level for Java, at the
  file-level for TypeScript)
- Code comments shouldn't just restate what the code already says, but provide a high-level
  description of what they are commenting. They should also provide context for how this
  code fits into a broader picture and reference other methods/classes/files as necessary.
- Maintainability is highly prized
    - It should be easy to evolve code over time (good boundaries, clear code, etc)
    - It should be easy to understand the code (not over-engineered)
    - It should be easy to undo changes (no spaghetti code)
