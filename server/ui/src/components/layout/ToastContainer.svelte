<script lang="ts">
  import { toast } from '../../stores/toast';
  import { flip } from 'svelte/animate';
  import { fly } from 'svelte/transition';
</script>

<div class="toast toast-end toast-bottom z-50">
    {#each $toast as t (t.id)}
        <div 
            animate:flip={{duration: 300}} 
            transition:fly={{y: 20, duration: 300}} 
            class="alert shadow-lg text-white min-w-[300px] cursor-pointer
                {t.type === 'info' ? 'alert-info' : ''} 
                {t.type === 'success' ? 'alert-success' : ''} 
                {t.type === 'warning' ? 'alert-warning' : ''} 
                {t.type === 'error' ? 'alert-error' : ''}"
            onclick={() => toast.remove(t.id)}
            role="alert"
        >
            <span class="font-medium text-white">{t.message}</span>
        </div>
    {/each}
</div>
