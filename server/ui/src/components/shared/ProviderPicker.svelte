<script lang="ts">
  export let providers: any[] = [];
  export let selectedValue: string | null = null;
  export let valueKey: string = 'name';
  export let title = 'Select Provider';
  export let description = '';
  export let buttonClass = 'btn btn-sm btn-outline';
  export let disabled = false;
  export let allowSearch = true;
  export let onchange: ((event: CustomEvent) => void) | null = null;

  let dialog: HTMLDialogElement | null = null;
  let search = '';
  let currentValue: string | null = null;

  $: if (selectedValue !== undefined) {
      currentValue = selectedValue ?? null;
  }

  $: selectedProvider = providers.find(
      (provider) => provider?.[valueKey] === currentValue
  );
  $: filteredProviders = !search
      ? providers
      : providers.filter((provider) => {
          const name = provider?.name?.toLowerCase() || '';
          const url = provider?.mainUrl?.toLowerCase() || '';
          const lang = provider?.lang?.toLowerCase() || '';
          const query = search.toLowerCase();
          return name.includes(query) || url.includes(query) || lang.includes(query);
      });

  function openDialog() {
      if (disabled) return;
      search = '';
      dialog?.showModal();
  }

  function closeDialog() {
      dialog?.close();
  }

  function selectProvider(provider: any) {
      const value = provider?.[valueKey];
      if (!value) return;
      currentValue = value;
      const event = new CustomEvent('change', { detail: { value, provider } });
      onchange?.(event);
      closeDialog();
  }
</script>

<button class={buttonClass} onclick={openDialog} disabled={disabled}>
  <span class="flex items-center gap-2 truncate">
      <span class="font-semibold truncate">
          {selectedProvider?.name || title}
      </span>
      {#if selectedProvider?.lang}
          <span class="badge badge-sm badge-neutral">{selectedProvider.lang}</span>
      {/if}
  </span>
  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 opacity-60" viewBox="0 0 24 24" fill="none" stroke="currentColor">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
  </svg>
</button>

<dialog class="modal" bind:this={dialog}>
  <div class="modal-box max-w-5xl">
      <div class="flex items-start justify-between gap-4">
          <div>
              <h3 class="text-lg font-bold">{title}</h3>
              {#if description}
                  <p class="text-sm opacity-60 mt-1">{description}</p>
              {/if}
          </div>
          <button class="btn btn-sm btn-ghost" onclick={closeDialog}>Close</button>
      </div>

      {#if allowSearch}
          <div class="mt-4">
              <input
                  class="input input-bordered w-full"
                  placeholder="Search providers..."
                  bind:value={search}
              />
          </div>
      {/if}

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-4 max-h-[60vh] overflow-y-auto pr-1">
          {#each filteredProviders as provider}
              <button
                  class="card bg-base-100 border border-base-content/10 hover:border-primary/60 text-left transition-colors"
                  onclick={() => selectProvider(provider)}
              >
                  <div class="card-body p-4 gap-2">
                      <div class="flex items-start justify-between gap-3">
                          <div class="min-w-0">
                              <h4 class="font-bold truncate">{provider.name}</h4>
                              <p class="text-xs opacity-60 truncate">{provider.mainUrl}</p>
                          </div>
                          {#if provider.lang}
                              <span class="badge badge-sm badge-outline">{provider.lang}</span>
                          {/if}
                      </div>
                      {#if provider.supportedTypes?.length}
                          <div class="flex flex-wrap gap-1">
                              {#each provider.supportedTypes.slice(0, 3) as type}
                                  <span class="badge badge-xs badge-ghost">{type}</span>
                              {/each}
                              {#if provider.supportedTypes.length > 3}
                                  <span class="badge badge-xs badge-ghost">+{provider.supportedTypes.length - 3}</span>
                              {/if}
                          </div>
                      {/if}
                  </div>
              </button>
          {/each}
          {#if filteredProviders.length === 0}
              <div class="col-span-full py-10 text-center text-sm opacity-60">
                  No providers found.
              </div>
          {/if}
      </div>
  </div>
  <form method="dialog" class="modal-backdrop">
      <button>close</button>
  </form>
</dialog>
