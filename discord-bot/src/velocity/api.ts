import type { Config } from '../config.js';

export interface Application {
  id: number;
  status: 'PENDING' | 'APPROVED' | 'DENIED';
  realName: string;
  discordTag?: string;
  inviter?: string;
  notes?: string;
  createdAt: number;
  decidedAt?: number;
  decidedBy?: string;
  decisionNote?: string;
  player?: {
    uuid: string;
    username: string;
    platform: 'JAVA' | 'BEDROCK';
  };
}

export interface ApproveRequest {
  servers: string[];
  admin: string;
  note?: string;
}

export interface DenyRequest {
  admin: string;
  reason?: string;
}

export interface ApiResponse<T = unknown> {
  success?: boolean;
  ok?: boolean;
  error?: string;
  data?: T;
}

export class VelocityApi {
  private readonly baseUrl: string;
  private readonly secret: string;

  constructor(config: Config) {
    this.baseUrl = config.velocity.apiUrl;
    this.secret = config.velocity.sharedSecret;
  }

  private async request<T>(
    method: string,
    path: string,
    body?: unknown
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;

    const response = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        'X-Shared-Secret': this.secret,
      },
      body: body ? JSON.stringify(body) : undefined,
    });

    const data = await response.json() as T;

    if (!response.ok) {
      const errorData = data as ApiResponse;
      throw new Error(errorData.error || `API error: ${response.status}`);
    }

    return data;
  }

  async getApplication(id: number): Promise<Application> {
    return this.request<Application>('GET', `/api/applications/${id}`);
  }

  async getPendingApplications(): Promise<{ applications: Application[] }> {
    return this.request<{ applications: Application[] }>(
      'GET',
      '/api/applications?status=PENDING'
    );
  }

  async approveApplication(
    id: number,
    servers: string[],
    adminDiscordId: string,
    note?: string
  ): Promise<ApiResponse> {
    return this.request<ApiResponse>('POST', `/api/applications/${id}/approve`, {
      servers,
      admin: `discord:${adminDiscordId}`,
      note,
    });
  }

  async denyApplication(
    id: number,
    adminDiscordId: string,
    reason?: string
  ): Promise<ApiResponse> {
    return this.request<ApiResponse>('POST', `/api/applications/${id}/deny`, {
      admin: `discord:${adminDiscordId}`,
      reason,
    });
  }

  async grantAccess(
    uuid: string,
    serverId: string,
    adminDiscordId: string,
    note?: string
  ): Promise<ApiResponse> {
    return this.request<ApiResponse>('POST', '/api/entitlements/grant', {
      uuid,
      serverId,
      admin: `discord:${adminDiscordId}`,
      note,
    });
  }

  async revokeAccess(
    uuid: string,
    serverId: string,
    adminDiscordId: string
  ): Promise<ApiResponse> {
    return this.request<ApiResponse>('POST', '/api/entitlements/revoke', {
      uuid,
      serverId,
      admin: `discord:${adminDiscordId}`,
    });
  }

  async getHealth(): Promise<{ status: string; stats: unknown }> {
    return this.request<{ status: string; stats: unknown }>('GET', '/api/health');
  }
}
