<script lang="ts">
  import fallbackPoster from '../../assets/poster-fallback.svg';

  export let title: string;
  export let image: string;
  export let subtitle: string | undefined = undefined;
  export let onSelect: (() => void) | undefined = undefined;

  const fallbackPosterSrc = fallbackPoster;

  // Fallback for missing images
  function handleImageError(e: Event) {
      const target = e.target as HTMLImageElement;
      if (target.src !== fallbackPosterSrc) {
          target.src = fallbackPosterSrc;
      }
  }
</script>

<div
    class="card bg-base-100 shadow-xl hover:scale-105 transition-transform duration-200 cursor-pointer overflow-hidden group h-full"
    role="button"
    tabindex="0"
    onclick={() => onSelect?.()}
    onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && onSelect?.()}
>
  <figure class="aspect-[2/3] relative">
      <img 
          src={image} 
          alt={title} 
          class="w-full h-full object-cover"
          onerror={handleImageError}
          loading="lazy"
      />
      <!-- Hover Overlay -->
      <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
          <button class="btn btn-circle btn-primary btn-lg scale-0 group-hover:scale-100 transition-transform duration-300">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clip-rule="evenodd" />
              </svg>
          </button>
      </div>
  </figure>
  <div class="card-body p-3 gap-1">
      <h3 class="font-bold text-sm line-clamp-2 leading-tight">{title}</h3>
      {#if subtitle}
          <p class="text-xs text-base-content/60">{subtitle}</p>
      {/if}
  </div>
</div>
