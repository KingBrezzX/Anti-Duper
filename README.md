# BedrockAntiDupe

Advanced event-driven anti-duplication protection for Paper servers.

Designed for:

- Paper 26.2
- Java 25
- Java Edition
- Bedrock players through Geyser/Floodgate

## Features

### Container Protection

Protects transactions involving:

- Shulker Boxes
- Chests
- Trapped Chests
- Ender Chests
- Barrels
- Hoppers
- Droppers
- Dispensers

### Duplication Transaction Protection

The plugin monitors suspicious transaction boundaries instead of continuously scanning the entire world.

Protected transaction paths include:

- Inventory clicks
- Inventory dragging
- Container movement
- Hopper movement
- Piston movement
- Piston retraction
- TNT/entity explosions
- Container explosions
- Shulker breaking
- Shop GUI transactions
- Shop commands
- Sell transactions
- Buy transactions
- Order transactions
- Auction/market transactions

### Java and Bedrock

Both Java and Bedrock players can be protected.

Bedrock detection uses Floodgate when available.

Java protection remains enabled independently.

### Discord Alerts

Suspicious transactions can be sent to a Discord webhook.

Discord requests are asynchronous so network latency does not block the main server thread.

Per-player cooldowns prevent Discord spam.

### Staff Alerts

Staff members with:

    bedrockantidupe.alert

can receive in-game alerts.

Alerts are rate limited.

### Evidence

Detection evidence can be stored asynchronously in:

    plugins/BedrockAntiDupe/evidence/

Each player receives a separate evidence file.

Example:

    plugins/BedrockAntiDupe/evidence/<UUID>.log

### Actions

Available modes:

    ALERT
    REMOVE
    REMOVE_AND_ALERT

`ALERT`

Only reports the suspicious transaction.

`REMOVE`

Removes invalid impossible stacks.

`REMOVE_AND_ALERT`

Removes invalid impossible stacks and alerts staff/Discord.

## Important Safety Behavior

BedrockAntiDupe does not globally disable TNT.

It does not globally disable pistons.

It does not disable normal shulker mechanics.

It does not continuously scan every loaded chunk.

It uses event-driven validation to minimize server overhead.

## Installation

1. Build the plugin.

2. Put:

    BedrockAntiDupe-1.0.0.jar

   into:

    plugins/

3. Restart the server.

4. Edit:

    plugins/BedrockAntiDupe/config.yml

5. Restart the server or run:

    /antidupe reload

## Permissions

### Administration

    bedrockantidupe.admin

Allows:

    /antidupe reload
    /antidupe status
    /antidupe check <player>
    /antidupe violations

### Staff alerts

    bedrockantidupe.alert

Receives anti-dupe alerts.

## Commands

### Reload

    /antidupe reload

Reloads the configuration.

### Status

    /antidupe status

Shows plugin status and detection mode.

### Check

    /antidupe check <player>

Displays:

- Player
- Platform
- Protected status
- Violations
- Invalid stack status

### Violations

    /antidupe violations

Shows the number of players currently tracked by the violation system.

## Discord Configuration

Open:

    plugins/BedrockAntiDupe/config.yml

Set:

    discord:
      enabled: true
      webhook-url: "YOUR_WEBHOOK"

Never commit a real Discord webhook URL to GitHub.

## Recommended Production Configuration

Start with:

    action:
      mode: REMOVE_AND_ALERT

    punishment:
      enabled: false

Test the detector first.

After confirming that legitimate transactions are not being flagged, punishment can be enabled if desired.

## Performance

The plugin is designed around event-driven detection.

It does not perform a permanent world-wide scan.

It does not scan unloaded chunks.

Discord communication is asynchronous.

Evidence writes are asynchronous.

Alert cooldowns reduce repeated processing.

## Troubleshooting

### Plugin does not start

Check:

- Java version
- Paper version
- plugin.yml
- server console

Required environment:

    Paper 26.2
    Java 25

### Bedrock detection does not work

Make sure Floodgate is installed and running.

The anti-dupe system can still protect Java players without Floodgate.

### Discord does not send

Check:

    discord.enabled: true

and:

    discord.webhook-url: "..."

Also check:

    console.errors: true

## Security

Do not publish:

- Discord webhook URLs
- server credentials
- database passwords
- private configuration
- staff-only information

## License

Private server plugin.

Copyright ZyrexSMP.
