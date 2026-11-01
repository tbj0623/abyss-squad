# Security and responsible disclosure

Abyss Squad is a local Android game. Its multiplayer mode is designed for a **trusted local network only**. The TCP game server has no authentication, TLS, public matchmaking, or Internet-facing threat model. Do not port-forward or expose port 45454 to the public Internet.

## Reporting issues

Please open a private security advisory on the GitHub repository if available, rather than posting a working exploit or private data publicly.

## Signing material

Release keystores, passwords, local.properties, tokens and other machine-specific credentials must never be committed. The public repository intentionally excludes the original private Git history and signing configuration.

## Privacy

The game has no advertising SDK, in-app purchases, account registration, or telemetry according to the current source implementation. Progress stays on the player's device; local multiplayer exchanges gameplay state with peers on the same network.
