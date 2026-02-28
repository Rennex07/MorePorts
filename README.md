# MorePorts

A simple Paper/Purpur plugin that allows your Minecraft server to listen on multiple ports simultaneously.

## Features

- **Multi-Port Listening (Java)**: Bind your server to additional ports (e.g., 25566, 25567) alongside the main port.
- **Bedrock Support (Geyser)**: Forwards traffic from extra UDP ports to your main Geyser instance.
- **No Proxy Required**: Runs entirely within your existing server instance.
- **Native Performance**: Uses the server's internal Netty stack for optimal performance.

## Installation

1. Stop your server.
2. Place `MorePorts-1.0-SNAPSHOT.jar` into your `plugins` folder.
3. Start the server.
4. Edit `plugins/MorePorts/config.yml` to add your desired extra ports.
5. Run `/moreports reload` or restart the server.

## Configuration

```yaml
# Ports to listen on for Java Edition
ports:
  - 25566
  - 25567

# Bedrock Edition (Geyser) Configuration
bedrock:
  enabled: true
  target-port: 19132     # Your main Geyser port
  target-address: "127.0.0.1"
  ports:
    - 19133
    - 19134
```

## Caveats

- **Bedrock IP Addresses**: Players connecting via the extra Bedrock ports will appear to the server/Geyser as connecting from `127.0.0.1` because of the simple forwarding mechanism. This means IP-based banning/whitelisting might not work correctly for these specific ports. Java Edition ports do **not** have this issue and show the real IP.

## Commands

- `/moreports status`: View active extra ports.
- `/moreports reload`: Reload configuration and bind new ports.

## Permissions

- `moreports.admin`: Access to all plugin commands (default: op).
