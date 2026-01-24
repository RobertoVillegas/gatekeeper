import {
  EmbedBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  StringSelectMenuBuilder,
  StringSelectMenuOptionBuilder,
  Colors,
} from 'discord.js';
import type { Application } from '../velocity/api.js';

const COLORS = {
  PENDING: 0xfee75c,   // Yellow
  APPROVED: 0x57f287,  // Green
  DENIED: 0xed4245,    // Red
};

export function buildApplicationEmbed(
  application: Application,
  availableServers: string[] = []
): { embed: EmbedBuilder; components: ActionRowBuilder<ButtonBuilder | StringSelectMenuBuilder>[] } {
  const player = application.player;
  const platformEmoji = player?.platform === 'BEDROCK' ? '🪨' : '☕';

  const embed = new EmbedBuilder()
    .setTitle('📋 New Access Request')
    .setColor(COLORS[application.status])
    .setTimestamp(new Date(application.createdAt * 1000));

  // Player info
  if (player) {
    embed.addFields(
      { name: 'Player', value: player.username, inline: true },
      { name: 'UUID', value: `\`${player.uuid}\``, inline: true },
      { name: 'Platform', value: `${platformEmoji} ${player.platform}`, inline: true }
    );
  }

  // Application data
  embed.addFields(
    { name: '\u200B', value: '───────────────────────────', inline: false },
    { name: 'Real Name', value: application.realName, inline: true },
    { name: 'Discord', value: application.discordTag || '—', inline: true },
    { name: 'Invited By', value: application.inviter || '—', inline: true }
  );

  if (application.notes) {
    embed.addFields({ name: 'Notes', value: application.notes, inline: false });
  }

  // Status footer
  const statusText = application.status === 'PENDING'
    ? '🟡 Pending'
    : application.status === 'APPROVED'
    ? `✅ Approved by ${application.decidedBy}`
    : `❌ Denied by ${application.decidedBy}`;

  embed.addFields(
    { name: '\u200B', value: '───────────────────────────', inline: false },
    { name: 'Status', value: statusText, inline: true }
  );

  if (application.status !== 'PENDING' && application.decidedAt) {
    embed.addFields({
      name: 'Decided',
      value: `<t:${application.decidedAt}:R>`,
      inline: true,
    });
  }

  // Build components based on status
  const components: ActionRowBuilder<ButtonBuilder | StringSelectMenuBuilder>[] = [];

  if (application.status === 'PENDING') {
    // Approve/Deny buttons
    const buttonRow = new ActionRowBuilder<ButtonBuilder>().addComponents(
      new ButtonBuilder()
        .setCustomId(`approve_quick_${application.id}`)
        .setLabel('Approve')
        .setStyle(ButtonStyle.Success)
        .setEmoji('✅'),
      new ButtonBuilder()
        .setCustomId(`approve_select_${application.id}`)
        .setLabel('Approve + Select')
        .setStyle(ButtonStyle.Primary)
        .setEmoji('🎯'),
      new ButtonBuilder()
        .setCustomId(`deny_${application.id}`)
        .setLabel('Deny')
        .setStyle(ButtonStyle.Danger)
        .setEmoji('❌')
    );
    components.push(buttonRow);
  } else if (application.status === 'APPROVED') {
    // Modify access button
    const buttonRow = new ActionRowBuilder<ButtonBuilder>().addComponents(
      new ButtonBuilder()
        .setCustomId(`modify_${application.id}_${player?.uuid || ''}`)
        .setLabel('Modify Access')
        .setStyle(ButtonStyle.Secondary)
        .setEmoji('🔧')
    );
    components.push(buttonRow);
  }

  return { embed, components };
}

export function buildServerSelectMenu(
  applicationId: number,
  availableServers: string[],
  defaultServers: string[] = []
): ActionRowBuilder<StringSelectMenuBuilder> {
  const options = availableServers.map((server) =>
    new StringSelectMenuOptionBuilder()
      .setLabel(server)
      .setValue(server)
      .setDefault(defaultServers.includes(server))
  );

  const select = new StringSelectMenuBuilder()
    .setCustomId(`server_select_${applicationId}`)
    .setPlaceholder('Select servers to grant access')
    .setMinValues(1)
    .setMaxValues(availableServers.length)
    .addOptions(options);

  return new ActionRowBuilder<StringSelectMenuBuilder>().addComponents(select);
}

export function buildApprovedEmbed(
  application: Application,
  servers: string[],
  adminTag: string
): EmbedBuilder {
  const player = application.player;
  const platformEmoji = player?.platform === 'BEDROCK' ? '🪨' : '☕';

  const embed = new EmbedBuilder()
    .setTitle('✅ Access Request Approved')
    .setColor(COLORS.APPROVED)
    .setTimestamp(new Date(application.createdAt * 1000));

  // Player info
  if (player) {
    embed.addFields(
      { name: 'Player', value: player.username, inline: true },
      { name: 'UUID', value: `\`${player.uuid}\``, inline: true },
      { name: 'Platform', value: `${platformEmoji} ${player.platform}`, inline: true }
    );
  }

  // Application data
  embed.addFields(
    { name: '\u200B', value: '───────────────────────────', inline: false },
    { name: 'Real Name', value: application.realName, inline: true },
    { name: 'Discord', value: application.discordTag || '—', inline: true },
    { name: 'Invited By', value: application.inviter || '—', inline: true }
  );

  if (application.notes) {
    embed.addFields({ name: 'Notes', value: application.notes, inline: false });
  }

  // Decision info
  embed.addFields(
    { name: '\u200B', value: '───────────────────────────', inline: false },
    { name: 'Status', value: `✅ Approved by ${adminTag}`, inline: true },
    { name: 'Access Granted', value: servers.join(', '), inline: true },
    { name: 'Decided', value: `<t:${Math.floor(Date.now() / 1000)}:R>`, inline: true }
  );

  return embed;
}

export function buildDeniedEmbed(
  application: Application,
  reason: string | undefined,
  adminTag: string
): EmbedBuilder {
  const player = application.player;
  const platformEmoji = player?.platform === 'BEDROCK' ? '🪨' : '☕';

  const embed = new EmbedBuilder()
    .setTitle('❌ Access Request Denied')
    .setColor(COLORS.DENIED)
    .setTimestamp(new Date(application.createdAt * 1000));

  // Player info
  if (player) {
    embed.addFields(
      { name: 'Player', value: player.username, inline: true },
      { name: 'UUID', value: `\`${player.uuid}\``, inline: true },
      { name: 'Platform', value: `${platformEmoji} ${player.platform}`, inline: true }
    );
  }

  // Application data
  embed.addFields(
    { name: '\u200B', value: '───────────────────────────', inline: false },
    { name: 'Real Name', value: application.realName, inline: true },
    { name: 'Discord', value: application.discordTag || '—', inline: true },
    { name: 'Invited By', value: application.inviter || '—', inline: true }
  );

  if (application.notes) {
    embed.addFields({ name: 'Notes', value: application.notes, inline: false });
  }

  // Decision info
  embed.addFields(
    { name: '\u200B', value: '───────────────────────────', inline: false },
    { name: 'Status', value: `❌ Denied by ${adminTag}`, inline: true },
    { name: 'Decided', value: `<t:${Math.floor(Date.now() / 1000)}:R>`, inline: true }
  );

  if (reason) {
    embed.addFields({ name: 'Reason', value: reason, inline: false });
  }

  return embed;
}
