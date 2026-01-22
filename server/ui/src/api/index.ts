export class CloudstreamAPI {
  private baseUrl: string;

  constructor(baseUrl: string = '/api') {
    this.baseUrl = baseUrl;
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const response = await fetch(`${this.baseUrl}${endpoint}`, options);
    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`API Error: ${response.status} ${response.statusText} - ${errorText}`);
    }
    return response.json();
  }

  // --- Config ---
  async getConfig(): Promise<any> {
    return this.request('/config');
  }

  async updateConfig(config: any): Promise<any> {
    return this.request('/config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config),
    });
  }

  // --- Repositories ---
  async getRepositories(): Promise<any[]> {
    return this.request('/repositories');
  }

  async addRepository(urlOrShortcode: string, name?: string): Promise<any> {
    const body: any = {};
    if (urlOrShortcode.startsWith('http')) {
      body.url = urlOrShortcode;
    } else {
      body.shortcode = urlOrShortcode;
    }
    if (name) body.name = name;

    return this.request('/repositories', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  }

  async removeRepository(id: string): Promise<any> {
    return this.request(`/repositories/${id}`, { method: 'DELETE' });
  }

  async getRepositoryPlugins(id: string): Promise<any> {
      return this.request(`/repositories/${id}/plugins`);
  }

  async installRepositoryPlugin(repoId: string, internalName: string): Promise<any> {
      return this.request(`/repositories/${repoId}/plugins/${internalName}/install`, {
          method: 'POST'
      });
  }

  // --- Plugins ---
  async getPlugins(): Promise<any[]> {
    return this.request('/plugins');
  }

  async installPlugin(repositoryUrl: string, internalName: string): Promise<any> {
    return this.request('/plugins/install', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ repositoryUrl, internalName }),
    });
  }

  async removePlugin(internalName: string): Promise<any> {
    return this.request('/plugins', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ internalName }),
    });
  }

  async uploadPlugin(file: File): Promise<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.request('/plugins/local', {
      method: 'POST',
      body: formData,
    });
  }

  // --- Providers ---
  async getProviders(): Promise<any[]> {
    return this.request('/providers');
  }

  async getProviderMainPage(providerName: string): Promise<any> {
    return this.request(`/providers/${providerName}/main-page`);
  }

  async searchProvider(providerName: string, query: string): Promise<any> {
    // Basic search; robust implementation would handle page pagination
    return this.request(`/providers/${providerName}/search?query=${encodeURIComponent(query)}`);
  }

  async loadMedia(providerName: string, url: string): Promise<any> {
    return this.request(`/providers/${providerName}/load?url=${encodeURIComponent(url)}`);
  }
}

export const api = new CloudstreamAPI();
