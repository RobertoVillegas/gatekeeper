export interface Config {
  discord: {
    token: string;
    applicationChannelId: string;
    adminRoleId?: string;
  };
  velocity: {
    apiUrl: string;
    sharedSecret: string;
  };
  server: {
    port: number;
    host: string;
  };
}

export function loadConfig(): Config {
  const requiredEnvVars = [
    'DISCORD_TOKEN',
    'DISCORD_CHANNEL_ID',
    'VELOCITY_API_URL',
    'VELOCITY_SHARED_SECRET',
  ];

  for (const envVar of requiredEnvVars) {
    if (!process.env[envVar]) {
      throw new Error(`Missing required environment variable: ${envVar}`);
    }
  }

  return {
    discord: {
      token: process.env.DISCORD_TOKEN!,
      applicationChannelId: process.env.DISCORD_CHANNEL_ID!,
      adminRoleId: process.env.DISCORD_ADMIN_ROLE_ID,
    },
    velocity: {
      apiUrl: process.env.VELOCITY_API_URL!,
      sharedSecret: process.env.VELOCITY_SHARED_SECRET!,
    },
    server: {
      port: parseInt(process.env.WEBHOOK_PORT || '3000', 10),
      host: process.env.WEBHOOK_HOST || '0.0.0.0',
    },
  };
}
