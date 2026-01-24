import {
  Client,
  GatewayIntentBits,
  Events,
  TextChannel,
} from 'discord.js';
import express from 'express';
import { loadConfig, type Config } from './config.js';
import { VelocityApi, type Application } from './velocity/api.js';
import { buildApplicationEmbed } from './discord/embeds.js';
import {
  handleButtonInteraction,
  handleSelectMenuInteraction,
  handleModalSubmit,
} from './handlers/interactions.js';

// Configuration
let config: Config;
let api: VelocityApi;
let discordClient: Client;
let applicationChannel: TextChannel | null = null;

// Server configuration (loaded from Velocity or configured here)
const availableServers = ['survival', 'smp2', 'creative'];
const defaultServers = ['survival'];

async function main() {
  console.log('Gatekeeper Discord Bot starting...');

  // Load configuration
  config = loadConfig();
  api = new VelocityApi(config);

  // Initialize Discord client
  discordClient = new Client({
    intents: [
      GatewayIntentBits.Guilds,
      GatewayIntentBits.GuildMessages,
    ],
  });

  // Discord ready event
  discordClient.once(Events.ClientReady, async (client) => {
    console.log(`Discord bot logged in as ${client.user.tag}`);

    // Get application channel
    const channel = await client.channels.fetch(config.discord.applicationChannelId);
    if (channel instanceof TextChannel) {
      applicationChannel = channel;
      console.log(`Application channel: #${channel.name}`);
    } else {
      console.error('Application channel not found or not a text channel');
    }
  });

  // Handle interactions (buttons, select menus, modals)
  discordClient.on(Events.InteractionCreate, async (interaction) => {
    try {
      if (interaction.isButton()) {
        await handleButtonInteraction(
          interaction,
          api,
          config,
          availableServers,
          defaultServers
        );
      } else if (interaction.isStringSelectMenu()) {
        await handleSelectMenuInteraction(interaction, api);
      } else if (interaction.isModalSubmit()) {
        await handleModalSubmit(interaction, api);
      }
    } catch (error) {
      console.error('Error handling interaction:', error);
    }
  });

  // Login to Discord
  await discordClient.login(config.discord.token);

  // Start webhook server for Velocity events
  startWebhookServer();
}

function startWebhookServer() {
  const app = express();
  app.use(express.json());

  // Authenticate requests from Velocity
  app.use('/events', (req, res, next) => {
    const secret = req.headers['x-shared-secret'];
    if (secret !== config.velocity.sharedSecret) {
      console.warn(`Unauthorized webhook request from ${req.ip}`);
      res.status(401).json({ error: 'Unauthorized' });
      return;
    }
    next();
  });

  // Handle incoming events from Velocity
  app.post('/events/application', async (req, res) => {
    try {
      const event = req.body;
      console.log('Received event:', event.type);

      if (event.type === 'NEW_APPLICATION') {
        await handleNewApplication(event);
      } else if (event.type === 'APPLICATION_DECIDED') {
        await handleApplicationDecided(event);
      }

      res.json({ success: true });
    } catch (error) {
      console.error('Error handling webhook:', error);
      res.status(500).json({ error: 'Internal error' });
    }
  });

  // Health check
  app.get('/health', (req, res) => {
    res.json({
      status: 'healthy',
      discord: discordClient.isReady(),
      channel: applicationChannel !== null,
    });
  });

  app.listen(config.server.port, config.server.host, () => {
    console.log(`Webhook server listening on ${config.server.host}:${config.server.port}`);
  });
}

async function handleNewApplication(event: {
  type: string;
  application: {
    id: number;
    player: { uuid: string; username: string; platform: string };
    realName: string;
    discordTag?: string;
    inviter?: string;
    notes?: string;
    submittedAt: number;
  };
  defaultServers: string[];
  availableServers: string[];
}) {
  if (!applicationChannel) {
    console.error('Application channel not available');
    return;
  }

  const application: Application = {
    id: event.application.id,
    status: 'PENDING',
    realName: event.application.realName,
    discordTag: event.application.discordTag,
    inviter: event.application.inviter,
    notes: event.application.notes,
    createdAt: event.application.submittedAt,
    player: {
      uuid: event.application.player.uuid,
      username: event.application.player.username,
      platform: event.application.player.platform as 'JAVA' | 'BEDROCK',
    },
  };

  const { embed, components } = buildApplicationEmbed(
    application,
    event.availableServers
  );

  const message = await applicationChannel.send({
    embeds: [embed],
    components: components as any,
  });

  console.log(`Posted application #${application.id} to Discord (message: ${message.id})`);
}

async function handleApplicationDecided(event: {
  type: string;
  applicationId: number;
  playerUsername: string;
  status: string;
  decidedBy: string;
  servers: string[];
  reason?: string;
}) {
  // This event is for updating embeds when decisions happen outside Discord
  // (e.g., via in-game commands)
  console.log(`Application #${event.applicationId} decided: ${event.status}`);

  // In a production system, you'd track message IDs and update the embed here
  // For now, we just log it
}

// Start the bot
main().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
