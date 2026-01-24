import {
  ButtonInteraction,
  StringSelectMenuInteraction,
  ModalSubmitInteraction,
  ModalBuilder,
  TextInputBuilder,
  TextInputStyle,
  ActionRowBuilder,
  MessageFlags,
} from 'discord.js';
import type { VelocityApi, Application } from '../velocity/api.js';
import {
  buildApprovedEmbed,
  buildDeniedEmbed,
  buildServerSelectMenu,
} from '../discord/embeds.js';
import type { Config } from '../config.js';

// Store application data temporarily for select menu flow
const pendingApprovals = new Map<string, { applicationId: number; application: Application }>();

export async function handleButtonInteraction(
  interaction: ButtonInteraction,
  api: VelocityApi,
  config: Config,
  availableServers: string[],
  defaultServers: string[]
): Promise<void> {
  const customId = interaction.customId;

  // Check admin permission if configured
  if (config.discord.adminRoleId) {
    const member = interaction.member;
    if (member && 'roles' in member) {
      const hasRole = member.roles instanceof Array
        ? member.roles.includes(config.discord.adminRoleId)
        : member.roles.cache.has(config.discord.adminRoleId);

      if (!hasRole) {
        await interaction.reply({
          content: '❌ You do not have permission to manage applications.',
          flags: MessageFlags.Ephemeral,
        });
        return;
      }
    }
  }

  if (customId.startsWith('approve_quick_')) {
    await handleQuickApprove(interaction, api, defaultServers);
  } else if (customId.startsWith('approve_select_')) {
    await handleApproveSelect(interaction, api, availableServers, defaultServers);
  } else if (customId.startsWith('deny_')) {
    await handleDenyModal(interaction);
  } else if (customId.startsWith('confirm_servers_')) {
    await handleConfirmServers(interaction, api);
  }
}

async function handleQuickApprove(
  interaction: ButtonInteraction,
  api: VelocityApi,
  defaultServers: string[]
): Promise<void> {
  const applicationId = parseInt(interaction.customId.split('_')[2], 10);

  await interaction.deferUpdate();

  try {
    // Get application details first
    const application = await api.getApplication(applicationId);

    // Approve with default servers
    await api.approveApplication(
      applicationId,
      defaultServers,
      interaction.user.id
    );

    // Update the message
    const embed = buildApprovedEmbed(application, defaultServers, interaction.user.tag);
    await interaction.editReply({
      embeds: [embed],
      components: [],
    });

    console.log(`Application #${applicationId} approved by ${interaction.user.tag}`);
  } catch (error) {
    console.error('Failed to approve application:', error);
    await interaction.followUp({
      content: `❌ Failed to approve: ${error instanceof Error ? error.message : 'Unknown error'}`,
      flags: MessageFlags.Ephemeral,
    });
  }
}

async function handleApproveSelect(
  interaction: ButtonInteraction,
  api: VelocityApi,
  availableServers: string[],
  defaultServers: string[]
): Promise<void> {
  const applicationId = parseInt(interaction.customId.split('_')[2], 10);

  try {
    // Get application details
    const application = await api.getApplication(applicationId);

    // Store for later use
    pendingApprovals.set(`${interaction.user.id}_${applicationId}`, {
      applicationId,
      application,
    });

    // Show server select menu
    const selectRow = buildServerSelectMenu(applicationId, availableServers, defaultServers);

    const confirmRow = new ActionRowBuilder<any>().addComponents(
      {
        type: 2, // Button
        style: 3, // Success
        label: 'Confirm',
        custom_id: `confirm_servers_${applicationId}`,
        emoji: { name: '✅' },
      },
      {
        type: 2, // Button
        style: 2, // Secondary
        label: 'Cancel',
        custom_id: `cancel_select_${applicationId}`,
      }
    );

    await interaction.reply({
      content: 'Select the servers to grant access to:',
      components: [selectRow, confirmRow],
      flags: MessageFlags.Ephemeral,
    });
  } catch (error) {
    console.error('Failed to show server select:', error);
    await interaction.reply({
      content: `❌ Failed to load servers: ${error instanceof Error ? error.message : 'Unknown error'}`,
      flags: MessageFlags.Ephemeral,
    });
  }
}

async function handleDenyModal(interaction: ButtonInteraction): Promise<void> {
  const applicationId = interaction.customId.split('_')[1];

  const modal = new ModalBuilder()
    .setCustomId(`deny_modal_${applicationId}`)
    .setTitle('Deny Application');

  const reasonInput = new TextInputBuilder()
    .setCustomId('reason')
    .setLabel('Reason (optional)')
    .setStyle(TextInputStyle.Paragraph)
    .setPlaceholder('Enter a reason for denial...')
    .setRequired(false)
    .setMaxLength(500);

  const row = new ActionRowBuilder<TextInputBuilder>().addComponents(reasonInput);
  modal.addComponents(row);

  await interaction.showModal(modal);
}

async function handleConfirmServers(
  interaction: ButtonInteraction,
  api: VelocityApi
): Promise<void> {
  // This is called after server selection
  // We need to get the selected servers from the message
  await interaction.reply({
    content: '✅ Please use the select menu above to choose servers, then this button will confirm.',
    flags: MessageFlags.Ephemeral,
  });
}

export async function handleSelectMenuInteraction(
  interaction: StringSelectMenuInteraction,
  api: VelocityApi
): Promise<void> {
  const customId = interaction.customId;

  if (customId.startsWith('server_select_')) {
    const applicationId = parseInt(customId.split('_')[2], 10);
    const selectedServers = interaction.values;

    await interaction.deferUpdate();

    try {
      // Get cached application data
      const key = `${interaction.user.id}_${applicationId}`;
      const cached = pendingApprovals.get(key);

      if (!cached) {
        // Fetch fresh
        const application = await api.getApplication(applicationId);
        await api.approveApplication(applicationId, selectedServers, interaction.user.id);

        // Update original message
        if (interaction.message.reference?.messageId) {
          // This is an ephemeral reply, update the original
        }

        await interaction.editReply({
          content: `✅ Approved with access to: ${selectedServers.join(', ')}`,
          components: [],
        });
      } else {
        await api.approveApplication(applicationId, selectedServers, interaction.user.id);
        pendingApprovals.delete(key);

        await interaction.editReply({
          content: `✅ Approved with access to: ${selectedServers.join(', ')}`,
          components: [],
        });
      }

      console.log(`Application #${applicationId} approved by ${interaction.user.tag} with servers: ${selectedServers.join(', ')}`);
    } catch (error) {
      console.error('Failed to approve with servers:', error);
      await interaction.followUp({
        content: `❌ Failed: ${error instanceof Error ? error.message : 'Unknown error'}`,
        flags: MessageFlags.Ephemeral,
      });
    }
  }
}

export async function handleModalSubmit(
  interaction: ModalSubmitInteraction,
  api: VelocityApi
): Promise<void> {
  const customId = interaction.customId;

  if (customId.startsWith('deny_modal_')) {
    const applicationId = parseInt(customId.split('_')[2], 10);
    const reason = interaction.fields.getTextInputValue('reason') || undefined;

    await interaction.deferUpdate();

    try {
      const application = await api.getApplication(applicationId);

      await api.denyApplication(applicationId, interaction.user.id, reason);

      const embed = buildDeniedEmbed(application, reason, interaction.user.tag);
      await interaction.editReply({
        embeds: [embed],
        components: [],
      });

      console.log(`Application #${applicationId} denied by ${interaction.user.tag}`);
    } catch (error) {
      console.error('Failed to deny application:', error);
      await interaction.followUp({
        content: `❌ Failed to deny: ${error instanceof Error ? error.message : 'Unknown error'}`,
        flags: MessageFlags.Ephemeral,
      });
    }
  }
}
