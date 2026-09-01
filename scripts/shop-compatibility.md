# Third-party shop compatibility gate

The plugin exposes a generic transaction identity/adapter boundary. A third-party shop is only marked compatible after its actual JAR is installed on Paper 26.2 and the following are exercised:

1. buy physical item
2. sell item
3. cancel/fail purchase
4. insufficient funds
5. rapid repeated purchase
6. disconnect during purchase
7. restart during purchase
8. verify inventory and economy conservation

Do not label an unspecified shop as compatible. Put the exact plugin name/version in the release report.

