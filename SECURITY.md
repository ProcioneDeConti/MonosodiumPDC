# Security Policy

MonosodiumPDC is a solo/hobby project. There's no dedicated security team and no formal SLA, but reports are taken seriously and addressed as quickly as reasonably possible.

## Supported Versions

Only the most recently published release is supported with security fixes. If you're not on the [latest release](https://github.com/ProcioneDeConti/MonosodiumPDC/releases/latest), please update before reporting - the issue may already be fixed.

## Scope

In scope:
- The app's own code: how it stores your e621 username/API key on-device, how it talks to the e621 API, DText/markup rendering, and anything else in this repository.

Out of scope:
- e621 itself (the website/API) - report those to e621 directly, not here.
- The Android OS, third-party libraries this app depends on (report upstream), or your device/network configuration.

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Reach out directly via Telegram: [@ProcioneDeConti](https://t.me/ProcioneDeConti).

If this repository is public when you're reading this, you can also check the **Security** tab for a **Report a vulnerability** option - GitHub's private reporting flow, which opens a conversation only you and the maintainer can see. (This isn't available while the repo is private, so Telegram is the reliable option for now.)

When reporting, please include:
- A description of the issue and its potential impact
- Steps to reproduce (device/Android version, app version, and what you did)
- Any relevant logs, screenshots, or proof-of-concept, if applicable

You'll get an acknowledgment as soon as possible, followed by updates as the issue is investigated and (if confirmed) fixed. Credit is welcome but optional - let me know your preference when reporting.
