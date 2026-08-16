# AuthSuite

Multi-provider hybrid identity for Minecraft servers. AuthSuite accepts and
resolves authentication through AuthInjector/BlessingSkin-style providers and
other authlib-compatible endpoints, routing player data, operator permissions,
and server-directed skins per provider.

Supports **Fabric**, **Forge**, and **NeoForge** across Minecraft **1.16.1 – 1.21.1**.

## Releases

Each loader/version combination ships as a tagged GitHub Release:

`<minecraft-version>-<release-version>-<loader>`

For example, `1.20.4-1.0-fabric`, `1.20.4-1.0-neoforge`, `1.16.5-1.0-forge`.

## Loader coverage

| Loader    | Minecraft versions                                      |
|-----------|---------------------------------------------------------|
| Fabric    | 1.16.1 – 1.21.1 (all minor releases)                    |
| Forge     | 1.16.1 – 1.20.1, 1.21.1                                  |
| NeoForge  | 1.20.2 – 1.21.1 (all minor releases)                    |

## Features

- Provider-isolated identity resolution (AuthInjector / BlessingSkin / authlib-compatible)
- Server-directed skins via authoritative `SkinDirective` payloads
- Provider-scoped operator permissions and player data stores
- Hybrid identity registry with fallthrough semantics

## License

Licensed under the **Cassi's Copyleft Environment Agnostic Software Enforcement License (CEASE), Version 1.0**.

CEASE requires the Corresponding Source of Modified Works to be publicly available.
There is no private-modification exception. See [LICENSE](LICENSE) for full terms.