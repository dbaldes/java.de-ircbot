# Agent Instructions

## Commit Message Format

All commits created by agents in this repository must use Conventional Commits.

- Format: `<type>(<scope>): <description>`
- Example: `feat(commands): add localtime command`
- Use lowercase type and description.
- Keep the description concise and imperative.

Common types:

- `feat`: new feature
- `fix`: bug fix
- `refactor`: code change that is neither a feature nor a fix
- `test`: test additions or changes
- `docs`: documentation-only changes
- `chore`: maintenance tasks

## Commit and Push Workflow

- Always create a commit for completed code changes without asking.
- Do not push commits unless explicitly asked by the user.
