# Contributing to FOLIO mod-search

Thank you for considering contributing to FOLIO mod-search! Your contributions are valuable and help us improve the project. Please take a moment to review this guide before making your contribution.

## Table of Contents

<!-- TOC -->
* [Contributing to FOLIO mod-search](#contributing-to-folio-mod-search)
  * [Table of Contents](#table-of-contents)
  * [How to Contribute](#how-to-contribute)
    * [Reporting Issues](#reporting-issues)
    * [Submitting Changes](#submitting-changes)
<!-- TOC -->

## How to Contribute

### Reporting Issues

If you encounter bugs, issues, or have any suggestions for improvements, please open an issue on [JIRA mod-search project](https://folio-org.atlassian.net/browse/MSEARCH). Provide as much detail as possible to help us understand and address the problem.

1. **Search for existing issues**: Before creating a new issue, please check if the issue has already been reported.
2. **Create a new issue**: If the issue does not exist, create a new issue using the provided template. Include a clear and descriptive title, a detailed description, steps to reproduce the issue, and any other relevant information.

### Submitting Changes

1. **Create a Branch**: Create a new branch for your changes.
    ```bash
    git checkout -b your-branch-name
    ```
2. **Make Changes**: Make your changes in your branch.
3. **Update Documentation**: If your changes affect existing behaviour or introduce new functionality, update the relevant feature documentation in [`docs/features/`](docs/features/). Each file in that directory corresponds to a feature area (e.g. `docs/features/instance-search.md`).
4. **Commit Changes**: Commit your changes with a meaningful commit message following [Conventional Commit practice](https://folio-org.atlassian.net/wiki/spaces/FOLIJET/pages/1400654/Conventional+Commit+Initiative).
    - The **scope** must match the `feature_id` — the file metadata of the relevant file under [`docs/features/`](docs/features/). For example, if your change is related to the "Instance Search" feature, the scope should be `instance-search` to match the `feature_id` in `docs/features/instance-search.md`.
    - The commit **footer** must include a `Closes` reference to the corresponding MSEARCH JIRA issue: `Closes: MSEARCH-XXX`.

    **Examples:**
    ```
    feat(instance-search): add support for searching by HRID

    Closes: MSEARCH-1234
    ```
    ```
    fix(reindex): resolve NPE during full reindex when tenant has no records

    Closes: MSEARCH-5678
    ```
    ```
    refactor(authority-search): simplify query translation logic and improve error handling

    Closes: MSEARCH-9101
    ```
    ```bash
    git add .
    git commit
    ```
5. **Push Changes**: Push your changes to the repository.
    ```bash
    git push origin your-branch-name
    ```
6. **Create a Pull Request**: Open a pull request from your branch to the `master` branch of the original repository. Follow [Pull Request Template](PULL_REQUEST_TEMPLATE.md) to create a description.
